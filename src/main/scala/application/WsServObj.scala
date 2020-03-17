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
import envs.CacheZLayerObject.CacheManager
import envs.EnvContainer
import envs.EnvContainer.{ZEnvLog, ZEnvLogCache}
import org.postgresql.PGNotification
import zio.logging.{LogLevel, Logging, log, logInfo, logTrace}
import zio.{Runtime, _}

import scala.Option
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps

//ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype
object WsServObj {

  //private val notifTimeout: Int = 3000 if listener connection is locked or not response

  /**
   *
   */
  private val cacheChecker: ZIO[ZEnvLogCache, Nothing, Unit] =
    for {
      cache <- ZIO.access[CacheManager](_.get)
      cacheCurrentValue <- cache.getCacheValue
      _ <- logInfo(s"cacheCurrentValue HeartbeatCounter = ${cacheCurrentValue.HeartbeatCounter}" +
        s" dictsMap.size = ${cacheCurrentValue.dictsMap.size}")
      _ <- cache.addHeartbeat
    } yield ()

  /**
   * Search Entity in Cache by tablename in  and remove it
   */
  private val removeFromCacheByRefTable: String => ZIO[ZEnvLogCache, Throwable, Unit] = tableName =>
    for {
      _ <- logInfo(s"DB Listener PID = ")
      cache <- ZIO.access[CacheManager](_.get)
      cv <- cache.getCacheValue
      _ <- logInfo(s"All keys = ${cv.dictsMap.keySet}")
      foundKeys: Seq[Int] = hashKeysForRemove(cv.dictsMap, tableName)
      _ <- logInfo(s"keys for removing from cache $foundKeys")
      _ <- cache.remove(foundKeys)
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

  private val cacheValidator: (DbConfig, PgConnection) => ZIO[ZEnvLogCache, Throwable, Unit] = (conf, pgsessSrc) => {
    import zio.duration._
    for {
      pgsessLs <- pgsessSrc.sess orElse
        PgConnection(conf).sess.retry(Schedule.recurs(3) && Schedule.spaced(2.seconds))

      _ <- logInfo(s"DB Listener PID = ${pgsessLs.pid}")
      notifications = scala.Option(pgsessLs.sess.getNotifications).getOrElse(Array[PGNotification]()) //timeout

      _ <- if (notifications.nonEmpty) {
        logInfo(s"notifications size = ${notifications.size}")
      } else {
        logInfo(s"notifications size = 0")
      }

    _ <- ZIO.foreach(notifications) { nt =>
        if (nt.getName == "change") {
          for {
            _ <- logInfo(s"Notif: name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}")
            _ <- removeFromCacheByRefTable(nt.getParameter)
          } yield UIO.succeed(())
        } else {
          UIO.succeed(())
        }
      }.catchAllCause {
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
   * Read config file and open Http server.
   * Example :
   * https://medium.com/@ghostdogpr/combining-zio-and-akka-to-enable-distributed-fp-in-scala-61ffb81e3283
   *
   */
  val WsServer: Config => ZIO[ZEnvLogCache, Throwable, Unit] = conf => {
    import zio.duration._
    val wsRes = Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
          cache <- ZIO.access[CacheManager](_.get)
          cacheInitialValue <- cache.get(1)
          _ <- logInfo(s"Before startRequestHandler. Cache created with $cacheInitialValue")
          fiber <- startRequestHandler(conf, actorSystem).forkDaemon//.fork(SuperviseMode.Disown)//.forkDaemon//.disconnect.fork
          _ <- fiber.join

          thisConnection = PgConnection(conf.dbListenConfig)
          cacheCheckerValidation <- cacheValidator(conf.dbListenConfig, thisConnection)
            .repeat(Schedule.spaced(3.second)).forkDaemon *>
            cacheChecker.repeat(Schedule.spaced(4.second)).forkDaemon
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


  /**
   * dbConfigList are registered list of databases from config file - application.conf
  */
  def reqHandlerM(dbConfigList: DbConfig, actorSystem: ActorSystem)(request: HttpRequest):
  Future[HttpResponse] = {
    implicit val system: ActorSystem = actorSystem

    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    import akka.http.scaladsl.unmarshalling.Unmarshal
    import ReqResp._

    lazy val responseFuture: ZIO[ZEnvLogCache, Throwable, HttpResponse] = request
    match {
      case request@HttpRequest(HttpMethods.POST, Uri.Path("/dicts"), _, _, _) => {
        val reqEntityString: Future[String] = Unmarshal(request.entity).to[String]
        routeDicts(request, dbConfigList, reqEntityString)
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
      responseFuture.provideLayer(envs.EnvContainer.ZEnvLogCacheLayer)
    )

  }


  /**
   * 1) runForeach(f: Out => Unit): Future[Done] - is a method of class "Source"
   *
   * 2)
   * handleWithAsyncHandler - is a method of class "IncomingConnection"
   * and it wait input parameter:
   * (handler: HttpRequest => Future[HttpResponse])
   *
   */
  val startRequestHandler: (Config, ActorSystem) => ZIO[ZEnvLogCache, Throwable, Future[Done]] =
    ( conf, actorSystem) => {
      implicit val system: ActorSystem = actorSystem
      import scala.concurrent.duration._
      implicit val timeout: Timeout = Timeout(120 seconds)
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher
      import akka.stream.scaladsl.Source
      for {
        ss: Source[Http.IncomingConnection, Future[ServerBinding]] <- serverSource(conf,actorSystem)
        _ <- logInfo("ServerSource created")
        cache <- ZIO.access[CacheManager](_.get)
        // Curried version of reqHandlerM has type HttpRequest => Future[HttpResponse]
        reqHandlerFinal <- Task(reqHandlerM(conf.dbConfig, actorSystem) _)
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

