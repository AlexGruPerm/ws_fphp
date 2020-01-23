package application

import environments.env.{AppTaskRes}
import zio.{IO, Managed, Task, UIO}
import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model._
import akka.stream.scaladsl._
import akka.util.Timeout
import confs.Config
import io.circe.syntax._
import io.circe.{ Json, Printer}
import zio.console.putStrLn
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps

/*
import java.util.concurrent.TimeUnit
import enironments.env.{AppTaskRes, RunResType}
import zio.{IO, Managed, Task, ZIO, console}
import akka.actor._
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.util.Timeout
import confs.Config
import io.circe.syntax._
import io.circe.{Encoder, Json, Printer}
import zio.console.putStrLn
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.language.postfixOps
*/

object WsServObj {

  private val logRequest: (LoggingAdapter,HttpRequest) => Unit = (log,req) => {
    log.info(s"================= ${req.method} REQUEST ${req.protocol.value} =============")
    log.info(s"uri : ${req.uri} ")
    log.info("  ---------- HEADER ---------")
    req.headers.zipWithIndex.foreach(hdr => log.info(s"   #${hdr._2} : ${hdr._1.toString}"))
    log.info("  ---------------------------")
    log.info(s"entity ${req.entity.toString} ")
    log.info("========================================================")
  }

  /**
   * Read config file and open Http server.
   * Example :
   * https://medium.com/@ghostdogpr/combining-zio-and-akka-to-enable-distributed-fp-in-scala-61ffb81e3283
   *
   */
  val WsServer: Config => AppTaskRes[Unit] = conf => {
    val ActSys = ActorSystem("WsDb")
    //startRequestHandler(conf,ActSys)

    Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
          _ <- putStrLn("[3]Call startRequestHandler from WsServer.")
          reqHandlerResult <- startRequestHandler(conf, actorSystem)
          _ <- putStrLn("[6]After startRequestHandler from WsServer.")
        } yield reqHandlerResult
    )

  }

  def startRequestHandler(conf :Config, actorSystem: ActorSystem) :Task[Unit] = {
    implicit val system = actorSystem
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext = system.dispatcher
    val log = Logging(system,"WsDb")
    log.info(s"[4]Endpoint from config file address = ${conf.api.endpoint} port = ${conf.api.port}")
    val serverSource = Http(actorSystem).bind(interface = "127.0.0.1", port = 8080)

    val reqHandler1: HttpRequest => Future[HttpResponse] = {
      case request@HttpRequest(HttpMethods.GET, Uri.Path ("/"), httpHeader, requestEntity, requestProtocol)
      => logRequest(log,request)
        Future.successful {
        val resJson: Json = s"SimpleTestString ${request.uri}".asJson
        HttpResponse (
          StatusCodes.OK,
          entity = HttpEntity (`application/json`, Printer.noSpaces.print (resJson))
        )
      }
      case request: HttpRequest =>
        logRequest(log,request)
        request.discardEntityBytes() // important to drain incoming HTTP Entity stream
        Future.successful{HttpResponse(404, entity = "Unknown resource!")}
    }

    val reqHandler2 = Flow[HttpRequest]
      //.via(reactToConnectionFailure)
      .map { request =>
        logRequest(log,request)
        // simple streaming (!) "echo" response:
        // add here case request@HttpRequest(HttpMethods.GET, Uri.Path ("/"), httpHeader, requestEntity, requestProtocol)
        HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, request.entity.dataBytes))
      }

    serverSource.runForeach { connection =>
        log.info("Accepted new connection from " + connection.remoteAddress)
        connection.handleWith(reqHandler2)
      // connection.handleWithAsyncHandler(reqHandler)
      }

    log.info("[5]Step before return Task(1) from startRequestHandler.")
    UIO.succeed(())
  }

  /*
val bindingFuture: Future[Http.ServerBinding] =
  serverSource.to(Sink.foreach { connection =>
    connection.handleWithAsyncHandler(reqHandler)
  }).run
*/

  /*
  serverSource.to(Sink.foreach { connection =>
    log.info("Accepted new connection from " + connection.remoteAddress)
    connection.handleWithAsyncHandler(reqHandler)
  }).run
  .failed.foreach { ex =>
    log.error(ex, "Failed to bind.")
  }
  */


}