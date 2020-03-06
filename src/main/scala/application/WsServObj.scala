package application

import java.util.concurrent.Executors

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.util.{ByteString, Timeout}
import application.WsServObj.CommonTypes.IncConnSrvBind
import confs.{Config, DbConfig}
import data.{Cache, CacheEntity, DictDataRows}
import dbconn.{PgConnection, pgSess, pgSessListen}
import envs.EnvContainer
import envs.EnvContainer.ZEnvLog
import org.postgresql.PGNotification
import zio.logging.{LogLevel, Logging, log, logInfo, logTrace}
import zio.{Runtime, _}

import scala.Option
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps

//ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype
object WsServObj {

  private val notifTimeout: Int = 3000

  /**
   *
   */
  private val cacheChecker: (Ref[Cache]) => ZIO[ZEnvLog, Nothing, Unit] = (cache) =>
    for {
      cacheCurrentValue <- cache.get
      _ <- logInfo(s"cacheCurrentValue HeartbeatCounter = ${cacheCurrentValue.HeartbeatCounter}" +
        s" dictsMap.size = ${cacheCurrentValue.dictsMap.size}")
      _ <- cache.update(cv => cv.copy(HeartbeatCounter = cv.HeartbeatCounter + 1)) //todo: remove.
    } yield ()

  /**
   **
   *CREATE OR REPLACE FUNCTION notify_change() RETURNS TRIGGER AS $$
   * BEGIN
   * perform pg_notify('change', TG_TABLE_NAME);
   * RETURN NULL;
   * END;
   * $$ LANGUAGE plpgsql;
   **
   *drop TRIGGER trg_ch_listener_notify on listener_notify;
   **
   *create TRIGGER trg_ch_listener_notify
   * AFTER INSERT OR UPDATE OR DELETE ON listener_notify
   * FOR EACH statement EXECUTE PROCEDURE notify_change();
   *
   */

  private val cacheValidator: (Ref[Cache], DbConfig, PgConnection) => ZIO[ZEnvLog, Throwable, Unit] = (cache, conf, pgsessSrc) => {
    import zio.duration._
    for {
      pgsessLs <- pgsessSrc.sess orElse
        PgConnection(conf).sess.retry(Schedule.recurs(3) && Schedule.spaced(2.seconds))

      _ <- logInfo(s"DB Listener PID = ${pgsessLs.pid}")
      notifications = scala.Option(pgsessLs.sess.getNotifications).getOrElse(Array[PGNotification]()) //timeout

      _ <- //Wslogger.out(LogLevel.Info)(s"notifications size = ${notifications.size}")
        (if (notifications.size != 0) {
          logInfo(s"notifications size = ${notifications.size}")
        } else {
          logInfo(s"notifications size = 0")
        })

    _ <- (
      ZIO.foreach(notifications) { nt =>
        if (nt.getName == "change") {
          for {
            //_ <- log(LogLevel.Debug)(s"Notif: name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}")
            _ <- logInfo(s"Notif: name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}")
            //
            // todo: here we need search all hashKeys where exists reference on table nt.getParameter.
            //       and use it to clear cache entities.
            //
            _ <- removeFromCacheByRefTable(cache, nt.getParameter)
          } yield UIO.succeed(())
        } else {
          UIO.succeed(())
        }
      }
      ).catchAllCause {
      e => logInfo(s" cacheValidator Exception $e")
    }

  } yield ()
}

  /**
   * Is field reftables from Class CacheEntity contain given tableName
  */
  private def hashKeysForRemove(dictsMape: Map[Int, CacheEntity], tableName: String) :Seq[Int] =
    dictsMape.mapValues(v => v.reftables.contains(tableName)).withFilter(_._2).map(_._1).toSeq

  /**
   * Search Entity in Cache by tablename in  and remove it
  */
  private val removeFromCacheByRefTable: (Ref[Cache], String) => ZIO[ZEnvLog,Throwable,Unit] = (cache, tableName) =>
    //for {
      //_ <- zio.logging.locallyAnnotate(correlationId, "cache_entity_remover") {
        for {
          //_ <- log(LogLevel.Debug)(s"DB Listener PID = ")
          _ <- logInfo(s"DB Listener PID = ")
          cv <- cache.get
          _ <- logInfo(s"All keys = ${cv.dictsMap.keySet}")
          //_ <- log(LogLevel.Debug)(s"All keys = ${cv.dictsMap.keySet}")
          //produce new Map with mapValues where values are Boolean, filter it by true and get only keys.
          foundKeys :Seq[Int] = hashKeysForRemove( cv.dictsMap,tableName)
          _ <- logInfo(s"keys for removing from cache $foundKeys")
          //_ <- log(LogLevel.Debug)(s"keys for removing from cache $foundKeys")
          _ <- cache.update(cvu => cvu.copy(HeartbeatCounter = cvu.HeartbeatCounter + 1,
            dictsMap = cvu.dictsMap -- foundKeys))
     //   } yield ()
      //}.provideSomeM(env)
    } yield ()


    /*
        ZIO.foreach(pgsessLs.sess.getNotifications(3000))(nt =>
          log(LogLevel.Debug)(s"Notification name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}").provideSomeM(env)
        )
*/
      /*
      cacheCurrentValue <- cache.get
      _  <- zio.modules.modules.logging.logging.locallyAnnotate(correlationId,"cache_validator"){
        log(LogLevel.Debug)(s"cacheCurrentValue HeartbeatCounter = ${cacheCurrentValue.HeartbeatCounter}" +
          s" dictsMap.size = ${cacheCurrentValue.dictsMap.size}")
      }.provideSomeM(env)
      _ <- cache.update(cv => cv.copy(HeartbeatCounter = cv.HeartbeatCounter + 1))//todo: remove.
      */


  /**
   * Read config file and open Http server.
   * Example :
   * https://medium.com/@ghostdogpr/combining-zio-and-akka-to-enable-distributed-fp-in-scala-61ffb81e3283
   *
   */
  val WsServer: Config => ZIO[ZEnvLog, Throwable, Unit] = conf => {
    import zio.duration._
    val wsRes = Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
          cache <- Ref.make(Cache(0, Map(1 -> CacheEntity(DictDataRows("empty", 0L, 0L, 0L, List(List())),Seq()))))
          cacheInitialValue <- cache.get

          _ <- logInfo(s"Before startRequestHandler. Cache created with $cacheInitialValue")

          fiber <- startRequestHandler(cache, conf, actorSystem).disconnect.fork
          _ <- fiber.join

          thisConnection = (new PgConnection(conf.dbListenConfig))
          cacheCheckerValidation <- cacheValidator(cache, conf.dbListenConfig, thisConnection)

            .repeat(Schedule.spaced(3.second)).disconnect.fork *>
            cacheChecker(cache).repeat(Schedule.spaced(4.second)).disconnect.fork
          _ <- cacheCheckerValidation.join
          _ <- logInfo("After startRequestHandler, end of WsServer.")
        } yield ()
    )

    /** examples:
     * reqHandlerResult <- startRequestHandler(conf, actorSystem).flatMap(_ => ZIO.never)
     * //_  <- UIO.succeed(()).repeat(Schedule.spaced(1.second))
     */
    wsRes
  }

  object CommonTypes {
    type IncConnSrvBind = akka.stream.scaladsl.Source[IncomingConnection, Future[ServerBinding]]
  }

  //val timeoutSettings = ConnectionPoolSettings(actorSystem.settings.config).withIdleTimeout(10 minutes)
 //val connSettings = ClientConnectionSettings(actorSystem.settings.config).withIdleTimeout(3 seconds)

  val serverSource: (Config, ActorSystem) => ZIO[ZEnvLog, Throwable, IncConnSrvBind] =
    (conf, actorSystem) => for {

      //_  <- zio.logging.locallyAnnotate(correlationId,"server_source"){
     //   log(LogLevel.Info)(s"Create Source[IncConnSrvBind] with ${conf.api.endpoint}:${conf.api.port}") &&&
     //     log(LogLevel.Info)(s" In input config are configured dbname = ${conf.dbConfig.dbname} databases.")
     // }.provideSomeM(env)

      _ <- logInfo(s"Create Source[IncConnSrvBind] with ${conf.api.endpoint}:${conf.api.port}") &&&
           logInfo(s" In input config are configured dbname = ${conf.dbConfig.dbname} databases.")

      ss <- Task(Http(actorSystem).bind(interface = conf.api.endpoint, port = conf.api.port))
    } yield ss

  //implicit val ec: ExecutionContextExecutor = system.dispatcher //todo: remove
  //implicit val fixedTpEc = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(1))
  //Runtime.default.unsafeRunToFuture(responseFuture.provideLayer(EnvContainer.ZEnvLog).lock(zio.internal.Executor.fromExecutionContext(1)(fixedTpEc)))
  //Runtime.default.unsafeRunToFuture(responseFuture.provideLayer(EnvContainer.ZEnvLog).lock(zio.internal.Executor.fromExecutionContext(10)(ec)))

  /**
   * dbConfigList are registered list of databases from config file - application.conf
  */
  def reqHandlerM(dbConfigList: DbConfig, actorSystem: ActorSystem, cache: Ref[Cache])(request: HttpRequest):
  Future[HttpResponse] = {
    implicit val system: ActorSystem = actorSystem
    import akka.http.scaladsl.unmarshalling.Unmarshal
    import ReqResp._

    lazy val responseFuture: ZIO[ZEnvLog, Throwable, HttpResponse] = request
    match {
      case request@HttpRequest(HttpMethods.POST, Uri.Path("/dicts"), _, _, _) => {
        val reqEntityString: Future[String] = Unmarshal(request.entity).to[String]
        routeDicts(request, cache, dbConfigList, reqEntityString)
      }
      case request@HttpRequest(HttpMethods.GET, _, _, _, _) =>
        request match {
          case request@HttpRequest(_, Uri.Path("/debug"), _, _, _) => routeGetDebug(request)
          case request@HttpRequest(_, Uri.Path("/favicon.ico"), _, _, _) => routeGetFavicon(request)
        }
      case request: HttpRequest => {
        request.discardEntityBytes()
        route404(request)
      }
    }

    Runtime.default.unsafeRunToFuture(
      responseFuture.provideLayer(zio.ZEnv.live >>> envs.EnvContainer.ZEnvLogLayer)
    )

  }

  //todo: rc18 bug: https://github.com/zio/zio/issues/3013



  /**
   * 1) runForeach(f: Out => Unit): Future[Done] - is a method of class "Source"
   *
   * 2)
   * handleWithAsyncHandler - is a method of class "IncomingConnection"
   * and it wait input parameter:
   * (handler: HttpRequest => Future[HttpResponse])
   *
   */
  val startRequestHandler: (Ref[Cache], Config, ActorSystem) => ZIO[ZEnvLog, Throwable, Future[Done]] =
    (cache, conf, actorSystem) => {
    implicit val system: ActorSystem = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(120 seconds)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    import akka.stream.scaladsl.Source
      for {
        ss: Source[Http.IncomingConnection, Future[ServerBinding]] <- serverSource(conf,actorSystem)
        _ <- logInfo("ServerSource created")

        // Curried version of reqHandlerM has type HttpRequest => Future[HttpResponse]
        reqHandlerFinal <- Task(reqHandlerM(conf.dbConfig, actorSystem, cache) _)
        requestHandlerFunc: RIO[HttpRequest, Future[HttpResponse]] =
        ZIO.fromFunction((r: HttpRequest) =>  reqHandlerFinal(r))
        serverWithReqHandler: RIO[IncConnSrvBind, Future[Done]] =
        ZIO.fromFunction((srv: IncConnSrvBind) =>
          srv.runForeach {
            conn =>
              conn.handleWithAsyncHandler(
                r => Runtime.default.unsafeRun(requestHandlerFunc.provide(r))
              )
          }
        )
        sourceWithServer <- serverWithReqHandler.provide(ss)
      } yield sourceWithServer
  }


}

