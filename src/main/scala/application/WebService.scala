package application

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.util.Timeout
import envs.EnvContainer.IncConnSrvBind
import dbconn.PgConnection
import envs.EnvContainer.ZEnvLogCache
import zio.logging.log
import zio.{Runtime, _}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import CacheHelper._
import envs.{Config, DbConfig}

object WebService {

  private val notifTimeout: Int = 3000

  /**
   * Read config file and open Http server.
   * Example :
   * https://medium.com/@ghostdogpr/combining-zio-and-akka-to-enable-distributed-fp-in-scala-61ffb81e3283
   *
   */
  val startService: Task[Config] => ZIO[ZEnvLogCache, Throwable, Unit] = confInstance => {
    import zio.duration._
    Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
          _ <- CacheLog.out("WsServer",true)
          conf <- confInstance

          fiber <- startRequestHandler(conf, actorSystem).forkDaemon
          _ <- fiber.join

          thisConnection = PgConnection(conf.dbListenConfig)

          cacheCheckerValidator <- cacheValidator(conf.dbListenConfig, thisConnection)
            .repeat(Schedule.spaced(3.second)).forkDaemon *>
            cacheChecker.repeat(Schedule.spaced(2.second)).forkDaemon *>
            readUserInterrupt(fiber,actorSystem).repeat(
              Schedule.spaced(1.second)).forkDaemon

          /*
          checkTermSignal <- checkTerm.repeat(Schedule.spaced(3.second)).forkDaemon
          _ <- checkTermSignal.join
          */

          _ <- cacheCheckerValidator.join //todo: may be divide on 2 separate forkDeamon
        } yield ()
    )
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

