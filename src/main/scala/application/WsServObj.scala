package application

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
import logging.LoggerCommon._
import org.postgresql.PGNotification
import zio.logging.{LogLevel, log}
import zio._
import zio.console.putStrLn

import scala.Option
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

//ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype
object WsServObj {

  private val notifTimeout: Int = 3000

  /**
   *
   */
  private val cacheChecker: (Ref[Cache]) => ZIO[ZEnv, Nothing, Unit] = (cache) =>
    for {
      cacheCurrentValue <- cache.get
      _  <- zio.logging.locallyAnnotate(correlationId,"cache_checker"){
        log(LogLevel.Debug)(s"cacheCurrentValue HeartbeatCounter = ${cacheCurrentValue.HeartbeatCounter}" +
          s" dictsMap.size = ${cacheCurrentValue.dictsMap.size}")
      }.provideSomeM(env)
      _ <- cache.update(cv => cv.copy(HeartbeatCounter = cv.HeartbeatCounter + 1))//todo: remove.
    } yield ()

  /**

  CREATE OR REPLACE FUNCTION notify_change() RETURNS TRIGGER AS $$
    BEGIN
        perform pg_notify('change', TG_TABLE_NAME);
        RETURN NULL;
    END;
$$ LANGUAGE plpgsql;

drop TRIGGER trg_ch_listener_notify on listener_notify;

create TRIGGER trg_ch_listener_notify
AFTER INSERT OR UPDATE OR DELETE ON listener_notify
FOR EACH statement EXECUTE PROCEDURE notify_change();

   */
  private val cacheValidator: (Ref[Cache], pgSessListen) => Task[Unit] = (cache, pgsessLs) =>
    for {
      _ <- zio.logging.locallyAnnotate(correlationId, "cache_validator") {
        for {
          _ <- log(LogLevel.Debug)(s"DB Listener PID = ${pgsessLs.pid}")
          notifications = scala.Option(pgsessLs.sess.getNotifications).getOrElse(Array[PGNotification]()) //timeout

          _ <- (if (notifications.size != 0) {
            log(LogLevel.Debug)(s"notifications size = ${notifications.size}")
          } else {
            log(LogLevel.Debug)(s"notifications size = 0")
          })

          _ <- (
            ZIO.foreach(notifications) { nt =>
              if (nt.getName == "change" && nt.getParameter == "listener_notify") {
                for {
                  _ <- log(LogLevel.Debug)(s"Notif: name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}")
                  //
                  // todo: here we need search all hashKeys where exists reference on table nt.getParameter.
                  //       and use it to clear cache entities.
                  //
                  _ <- removeFromCacheByRefTable(cache,nt.getParameter)
                  _ <- cache.update(cv => cv.copy(HeartbeatCounter = cv.HeartbeatCounter + 1, dictsMap = cv.dictsMap - (1670615853, -1839933013)))
                } yield UIO.succeed(())
              } else {
                UIO.succeed(())
              }
            }
            ).catchAllCause {
            e => log(LogLevel.Debug)(s" cacheValidator Exception $e")
          }

        } yield ()
      }.provideSomeM(env)
    } yield ()


  /**
   * Is field reftables from Class CacheEntity contain given tableName
  */
  private def hashKeysForRemove(dictsMape: Map[Int, CacheEntity], tableName: String) :Seq[Int] =
    dictsMape.mapValues(v => v.reftables.contains(tableName)).withFilter(_._2).map(_._1).toSeq

  /**
   * Search Entity in Cache by tablename in  and remove it
  */
  private val removeFromCacheByRefTable: (Ref[Cache], String) => Task[Unit] = (cache, tableName) =>
    for {
      _ <- zio.logging.locallyAnnotate(correlationId, "cache_entity_remover") {
        for {
          _ <- log(LogLevel.Debug)(s"DB Listener PID = ")
          cv <- cache.get
          _ <- log(LogLevel.Debug)(s"All keys = ${cv.dictsMap.keySet}")
          //produce new Map with mapValues where values are Boolean, filter it by true and get only keys.
          foundKeys :Seq[Int] = hashKeysForRemove( cv.dictsMap,tableName)
          _ <- log(LogLevel.Debug)(s"keys for removing from cache $foundKeys")
          _ <- cache.update(cvu => cvu.copy(HeartbeatCounter = cvu.HeartbeatCounter + 1,
            dictsMap = cvu.dictsMap -- foundKeys))
        } yield ()
      }.provideSomeM(env)
    } yield ()


    /*
        ZIO.foreach(pgsessLs.sess.getNotifications(3000))(nt =>
          log(LogLevel.Debug)(s"Notification name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}").provideSomeM(env)
        )
*/
      /*
      cacheCurrentValue <- cache.get
      _  <- zio.logging.locallyAnnotate(correlationId,"cache_validator"){
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
  val WsServer: Config => ZIO[ZEnv, Throwable, Unit] = conf => {
    import zio.duration._
    val wsRes = Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
          cache <- Ref.make(Cache(0, Map(1 -> CacheEntity(DictDataRows("empty", 0L, 0L, 0L, List(List())),Seq()))))
          cacheInitialValue <- cache.get
          _ <- zio.logging.locallyAnnotate(correlationId, "wsserver") {
            log(LogLevel.Info)(s"Before startRequestHandler. Cache created with $cacheInitialValue")
          }.provideSomeM(env)

          fiber <- startRequestHandler(cache, conf, actorSystem).fork
          _ <- fiber.join

          thisConnection <- (new PgConnection).sess(conf.dbListenConfig)

          cacheCheckerValidaot <- cacheValidator(cache,thisConnection).repeat(Schedule.spaced(3.second)).fork *>
           cacheChecker(cache).repeat(Schedule.spaced(4.second)).fork
          _ <- cacheCheckerValidaot.join

          _ <- zio.logging.locallyAnnotate(correlationId, "wsserver") {
            log(LogLevel.Info)("After startRequestHandler, end of WsServer.")
          }.provideSomeM(env)

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

  val serverSource: (Config, ActorSystem) => ZIO[Any, Throwable, IncConnSrvBind] =
    (conf, actorSystem) => for {
      _  <- zio.logging.locallyAnnotate(correlationId,"server_source"){
        log(LogLevel.Info)(s"Create Source[IncConnSrvBind] with ${conf.api.endpoint}:${conf.api.port}") &&&
          log(LogLevel.Info)(s" In input config are configured dbname = ${conf.dbConfig.dbname} databases.")
      }.provideSomeM(env)
      ss <- Task(Http(actorSystem).bind(interface = conf.api.endpoint, port = conf.api.port))
    } yield ss

  /**
   * dbConfigList are registered list of databases from config file - application.conf
  */
  def reqHandlerM(dbConfigList: DbConfig, actorSystem: ActorSystem, cache: Ref[Cache])(request: HttpRequest):
  Future[HttpResponse] = {
    implicit val system: ActorSystem = actorSystem
    import scala.concurrent.duration._
    import akka.http.scaladsl.unmarshalling.Unmarshal
    implicit val timeout: Timeout = Timeout(60 seconds)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    import ReqResp._

    val responseFuture: ZIO[ZEnv, Throwable, HttpResponse] =
      request match {// m.b. future HttpMethods.OPTIONS for http/2
        case request@HttpRequest(HttpMethods.POST, Uri.Path("/dicts"), _, _, _) =>
      {
        val reqEntityString :Future[String] = Unmarshal(request.entity).to[String]
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

    (new DefaultRuntime {}).unsafeRunToFuture(responseFuture)
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
  val startRequestHandler: (Ref[Cache], Config, ActorSystem) => ZIO[ZEnv, Throwable, Future[Done]] =
    (cache, conf, actorSystem) => {
    implicit val system: ActorSystem = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(120 seconds)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    import akka.stream.scaladsl.Source
      for {
        ss: Source[Http.IncomingConnection, Future[ServerBinding]] <- serverSource(conf,actorSystem)
        _  <- zio.logging.locallyAnnotate(correlationId,"req-handler"){
          log(LogLevel.Info)(s"ServerSource created")
          }.provideSomeM(env)

        // Curried version of reqHandlerM has type HttpRequest => Future[HttpResponse]
        reqHandlerFinal <- Task(reqHandlerM(conf.dbConfig, actorSystem, cache) _)

        requestHandlerFunc: RIO[HttpRequest, Future[HttpResponse]] = ZIO.fromFunction((r: HttpRequest) =>
          reqHandlerFinal(r))

        serverWithReqHandler: RIO[IncConnSrvBind, Future[Done]] = ZIO.fromFunction((srv: IncConnSrvBind) =>
          srv.runForeach {
            conn => conn.handleWithAsyncHandler(r => new DefaultRuntime {}.unsafeRun(requestHandlerFunc.provide(r)))
          }
        )
        sourceWithServer <- serverWithReqHandler.provide(ss)
      } yield sourceWithServer
  }


}

