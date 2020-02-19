package application

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import akka.http.scaladsl._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.util.Timeout
import application.WsServObj.CommonTypes.IncConnSrvBind
import confs.{Config, DbConfig}
import logging.LoggerCommon._
import zio.logging.{LogLevel, log}
import zio._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

//ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype
object WsServObj {

  /**
   *
   */
  private val cacheChecker: Ref[Int] => ZIO[ZEnv, Nothing, Unit] = cache =>
    for {
       cacheCurrentValue <- cache.get
      _  <- zio.logging.locallyAnnotate(correlationId,"cache_checker"){
        log(LogLevel.Debug)(s"cacheCurrentValue = $cacheCurrentValue")
      }.provideSomeM(env)
      _ <- cache.update(_ + 1)
    } yield ()


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
          cache <- Ref.make(0)
          cacheInitialValue <- cache.get

          _  <- zio.logging.locallyAnnotate(correlationId,"wsserver"){
            log(LogLevel.Info)(s"Before startRequestHandler. Cache created with $cacheInitialValue")
          }.provideSomeM(env)

          fiber <- startRequestHandler(cache, conf, actorSystem).fork
          _ <- fiber.join
          _ <- cacheChecker(cache).repeat(Schedule.spaced(2.second))

          _  <- zio.logging.locallyAnnotate(correlationId,"wsserver"){
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

  val serverSource: (Config, ActorSystem) => ZIO[Any, Throwable, IncConnSrvBind] =
    (conf, actorSystem) => for {
      _  <- zio.logging.locallyAnnotate(correlationId,"server_source"){
        log(LogLevel.Info)(s"Create Source[IncConnSrvBind] with ${conf.api.endpoint}:${conf.api.port}") &&&
          log(LogLevel.Info)(s" In input config are configured ${conf.dbConfig.size} databases.")
      }.provideSomeM(env)
      ss <- Task(Http(actorSystem).bind(interface = conf.api.endpoint, port = conf.api.port))
    } yield ss

  /**
   * dbConfigList are registered list of databases from config file - application.conf
  */
  def reqHandlerM(dbConfigList: List[DbConfig], actorSystem: ActorSystem, cache: Ref[Int])(request: HttpRequest):
  Future[HttpResponse] = {
    implicit val system: ActorSystem = actorSystem
    import scala.concurrent.duration._
    import akka.http.scaladsl.unmarshalling.Unmarshal
    implicit val timeout: Timeout = Timeout(60 seconds)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    import ReqResp._

    val responseFuture: ZIO[ZEnv, Throwable, HttpResponse] =
      request match {
        case request@HttpRequest(HttpMethods.POST, Uri.Path("/dicts"), _, _, _) =>
      {
        val reqEntityString :Future[String] = Unmarshal(request.entity).to[String]
        routeDicts(request, cache, dbConfigList, reqEntityString)
      }
        case request@HttpRequest(HttpMethods.GET, _, _, _, _) =>
          request match {
            case request@HttpRequest(_, Uri.Path("/debug"), _, _, _) => routeGetDebug(request, cache)
            case request@HttpRequest(_, Uri.Path("/favicon.ico"), _, _, _) => routeGetFavicon(request)
          }
        case request: HttpRequest => {
          request.discardEntityBytes()
          route404(request)
        }
      }

    new DefaultRuntime {}.unsafeRunToFuture(responseFuture)
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
  val startRequestHandler: (Ref[Int], Config, ActorSystem) => ZIO[ZEnv, Throwable, Future[Done]] =
    (cache, conf, actorSystem) => {
    implicit val system: ActorSystem = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(60 seconds)
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

