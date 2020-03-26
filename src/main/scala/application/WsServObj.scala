package application

import java.util.concurrent.Executors

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.http.scaladsl.settings.{ClientConnectionSettings, ConnectionPoolSettings}
import akka.util.{ByteString, Timeout}
import envs.EnvContainer.IncConnSrvBind
import confs.{Config, DbConfig}
import data.{Cache, CacheEntity, DictDataRows}
import dbconn.{PgConnection, pgSess, pgSessListen}
import envs.CacheZLayerObject.CacheManager
import envs.EnvContainer
import envs.EnvContainer.{ZEnvLog, ZEnvLogCache}
import org.postgresql.PGNotification
import zio.logging.{LogLevel, Logging, log}
import zio.{Layer, Runtime, _}
import scala.Option
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.language.postfixOps
import CacheHelper._

//ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype
object WsServObj {

  //private val notifTimeout: Int = 3000 if listener connection is locked or not response

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
          _ <- CacheLog.out("WsServer",true)

          fiber <- startRequestHandler(conf, actorSystem).forkDaemon
          _ <- fiber.join

          thisConnection = PgConnection(conf.dbListenConfig)

          cacheCheckerValidation <- cacheValidator(conf.dbListenConfig, thisConnection)
            .repeat(Schedule.spaced(3.second)).forkDaemon *>
            cacheChecker.repeat(Schedule.spaced(2.second)).forkDaemon
          _ <- cacheCheckerValidation.join

          _ <- log.info("After startRequestHandler, end of WsServer.")
        } yield ()
    )

    /** examples:
     * reqHandlerResult <- startRequestHandler(conf, actorSystem).flatMap(_ => ZIO.never)
     * //_  <- UIO.succeed(()).repeat(Schedule.spaced(1.second))
     */
    wsRes
  }


  val serverSource: (Config, ActorSystem) => ZIO[ZEnvLogCache, Throwable, IncConnSrvBind] =
    (conf, actorSystem) => for {
      _ <- CacheLog.out("serverSource",true)
      _ <- log.info(s"Create Source[IncConnSrvBind] with ${conf.api.endpoint}:${conf.api.port}") &&&
           log.info(s" In input config are configured dbname = ${conf.dbConfig.dbname} databases.")
      ss <- Task(Http(actorSystem).bind(interface = conf.api.endpoint, port = conf.api.port))
    } yield ss


  /**
   * dbConfigList are registered list of databases from config file - application.conf
  */
  def reqHandlerM(dbConfigList: DbConfig, actorSystem: ActorSystem, rt: Runtime[ZEnvLogCache])(request: HttpRequest):
  Future[HttpResponse] = {
    implicit val system: ActorSystem = actorSystem

    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    import akka.http.scaladsl.unmarshalling.Unmarshal
    import ReqResp._

    lazy val responseFuture: ZIO[ZEnvLogCache, Throwable, HttpResponse] = request
    match {
      case request@HttpRequest(HttpMethods.POST, Uri.Path("/dicts"), _, _, _) =>
        val reqEntityString: Future[String] = Unmarshal(request.entity).to[String]
        routeDicts(request, dbConfigList, reqEntityString)
      case request@HttpRequest(HttpMethods.GET, _, _, _, _) =>
        request match {
          case request@HttpRequest(_, Uri.Path("/debug"), _, _, _) => routeGetDebug(request)
          case request@HttpRequest(_, Uri.Path("/favicon.ico"), _, _, _) => routeGetFavicon(request)
        }
      case request: HttpRequest =>
        request.discardEntityBytes()
        route404(request)
    }
    rt.unsafeRunToFuture(
      responseFuture
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
    (conf, actorSystem) => {
      implicit val system: ActorSystem = actorSystem
      import scala.concurrent.duration._
      implicit val timeout: Timeout = Timeout(120 seconds)
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher
      import akka.stream.scaladsl.Source
      for {
        _ <- CacheLog.out("startRequestHandler", true)
        rti: Runtime[ZEnvLogCache] <- ZIO.runtime[ZEnvLogCache]
        ss: Source[Http.IncomingConnection, Future[ServerBinding]] <- serverSource(conf, actorSystem)
        reqHandlerFinal <- RIO(reqHandlerM(conf.dbConfig, actorSystem, rti) _)
        serverWithReqHandler =
        ss.runForeach {
          conn =>
            conn.handleWithAsyncHandler(
              r => rti.unsafeRun(ZIO(reqHandlerFinal(r)))
            )
        }
        sourceWithServer <- ZIO.succeed(serverWithReqHandler)
      } yield sourceWithServer
    }

}

