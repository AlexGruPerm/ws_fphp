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

import scala.concurrent.Future
import scala.language.postfixOps


object WsServObj {

  //ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype

  /**
   *
   */
  val cacheChecker: Ref[Int] => ZIO[ZEnv, Nothing, Unit] = cache =>
    for {
      currValue <- cache.get
      _ <- putStrLn(s"cacheChecker state = $currValue")
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
    /**
     * .fork
     * Returns an effect that forks this effect into its own separate fiber,
     * returning the fiber immediately, without waiting for it to compute its
     * value.
     * .join
     * Joins the fiber, which suspends the joining fiber until the result of the fiber has been determined.
     */
    val wsRes = Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
          cache <- Ref.make(0)
          _ <- putStrLn("[3]Call startRequestHandler from WsServer.")
          fiber <- startRequestHandler(cache, conf, actorSystem).fork
          _ <- fiber.join
          _ <- cacheChecker(cache).repeat(Schedule.spaced(2.second))
          _ <- putStrLn("[6]After startRequestHandler from WsServer.")
        } yield ()
    )
    /** examples:
     * reqHandlerResult <- startRequestHandler(conf, actorSystem).flatMap(_ => ZIO.never)
     * //_  <- UIO.succeed(()).repeat(Schedule.spaced(1.second))
     */
    wsRes
  }

  val serverSource: ActorSystem => ZIO[Any, Throwable, akka.stream.scaladsl.Source[Http.IncomingConnection, Future[ServerBinding]]] = actorSystem => Task(
    Http(actorSystem).bind(interface = "127.0.0.1", port = 8080)
  )

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
 object CommonTypes {
    type IncConnSrvBind = akka.stream.scaladsl.Source[IncomingConnection, Future[ServerBinding]]
  }


  def reqHandlerM(actorSystem: ActorSystem, cache: Ref[Int])(request: HttpRequest): Future[HttpResponse] = {
    implicit val system = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext = system.dispatcher
    val log = Logging(system,"WsDb")
    import ReqResp._

    /**
     * unsafeRunToFuture
    */
    request match {
      //case request@HttpRequest(HttpMethods.POST, Uri.Path("/test"), _, _, _) => routPostTest(request,log)
      case request@HttpRequest(HttpMethods.GET, Uri.Path("/debug"), _, _, _) => routeGetDebug(request, cache, log)
      /*
      case request: HttpRequest => {request.discardEntityBytes()
        route404(request,log)
      }
        */
    }

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
        ss: Source[Http.IncomingConnection, Future[ServerBinding]] <- serverSource(actorSystem)

        currCacheValue <- cache.get
        _ <- putStrLn(s"startRequestHandler currCacheValue = $currCacheValue")
        _ <- cache.update(_ + 10)

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

