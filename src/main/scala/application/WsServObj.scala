package application

import akka.Done
import zio.{DefaultRuntime, Managed, RIO, Ref, Schedule, Task, UIO, ZEnv, ZIO}
import akka.actor.{ActorSystem, _}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import akka.http.scaladsl._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.util.Timeout
import confs.Config
import io.circe.syntax._
import io.circe.{Json, Printer}
import zio.console.putStrLn
import akka.http.scaladsl.model.HttpCharsets._
import application.WsServObj.CommonTypes.IncConnSrvBind
import zio.logging.{LogLevel, log}

import scala.concurrent.Future
import scala.language.postfixOps
import logging.LoggerCommon._


object WsServObj {

  //ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype

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
        log(LogLevel.Info)(s"Create Source[IncConnSrvBind] with ${conf.api.endpoint}:${conf.api.port}")
      }.provideSomeM(env)
      ss <- Task(Http(actorSystem).bind(interface = conf.api.endpoint, port = conf.api.port))
    } yield ss

  import scala.io.Source

  /* read example
val currCacheValUIO :UIO[Int] = cache.get
val runtime = new DefaultRuntime {}
val currCacheVal :Int = runtime.unsafeRun(currCacheValUIO)
log.info(s"currCacheVal = $currCacheVal")
 // write example
val currCacheValUIO :UIO[Int] = cache.get
val runtime = new DefaultRuntime {}
val currCacheVal :Int = runtime.unsafeRun(currCacheValUIO)
log.info(s" Before currCacheVal = $currCacheVal")
val currCacheValUIONew :UIO[Int] = cache.update(_ + 100)
val currCacheValNew :Int = runtime.unsafeRun(currCacheValUIONew)
log.info(s" After currCacheValNew = $currCacheValNew")
*/



  def reqHandlerM(actorSystem: ActorSystem, cache: Ref[Int])(request: HttpRequest): Future[HttpResponse] = {
    implicit val system = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext = system.dispatcher
    import ReqResp._

    val rt = new DefaultRuntime {}

    /**
      * unsafeRunToFuture
      */
    val responseFuture: ZIO[ZEnv, Throwable, HttpResponse] =
      request match {
        case request@HttpRequest(HttpMethods.POST, Uri.Path("/test"), _, _, _) =>
          routPostTest(request, cache)
        case request@HttpRequest(HttpMethods.GET, Uri.Path("/debug"), _, _, _) =>
          routeGetDebug(request, cache)
        case request@HttpRequest(HttpMethods.GET, Uri.Path("/favicon.ico"), _, _, _) =>
          routeGetFavicon(request)
        case request: HttpRequest => {
          request.discardEntityBytes()
          route404(request)
        }
      }

    rt.unsafeRunToFuture(responseFuture)
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
    implicit val system = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext = system.dispatcher
    import akka.stream.scaladsl.Source
      for {
        ss: Source[Http.IncomingConnection, Future[ServerBinding]] <- serverSource(conf,actorSystem)
        _  <- zio.logging.locallyAnnotate(correlationId,"req-handler"){
          log(LogLevel.Info)(s"ServerSource created")
          }.provideSomeM(env)
        /*
        currCacheValue <- cache.get
        _ <- putStrLn(s"startRequestHandler currCacheValue = $currCacheValue")
        _ <- cache.update(_ + 10)
        */
        reqHandlerFinal :(HttpRequest => Future[HttpResponse]) <- Task(reqHandlerM(actorSystem, cache) _) // Curried version of reqHandlerM

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

