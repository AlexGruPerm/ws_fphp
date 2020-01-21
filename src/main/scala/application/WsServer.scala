package application

import enironments.env.{AppTaskRes, RunResType}
import zio.{IO, Managed, Task, ZIO, console}
import akka.actor._
import akka.event.Logging
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

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps


/*
import Actors.Common.User
import Actors.Manager.ActorManager
import akka.actor._
import akka.event.Logging
import akka.http.scaladsl._
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Authorization
import akka.stream.scaladsl._
import akka.util.Timeout
import egt.HttpActorSystem.CommServerAS
import io.circe.syntax._
import io.circe.{Encoder, Json, Printer}

import scala.concurrent._
import scala.concurrent.duration._
import scala.language.postfixOps
*/

object application {

  /**
   * Read config file and open Http server.
   * Example :
   * https://medium.com/@ghostdogpr/combining-zio-and-akka-to-enable-distributed-fp-in-scala-61ffb81e3283
   *
   */
  val WsServer: Config => AppTaskRes[Int] = conf => {
    Managed.make(Task(ActorSystem("WsDb")))(sys => Task.fromFuture(_ => sys.terminate()).ignore).use(
      actorSystem =>
        for {
         reqHandlerResult <- startRequestHandler(conf,actorSystem)
        } yield reqHandlerResult
    )
  }

  def startRequestHandler(conf :Config, actorSystem: ActorSystem) :Task[Int] = {
    implicit val system = actorSystem
    implicit val timeout: Timeout = Timeout(10 seconds)
    val log = Logging(system,"WsDb")
    log.info(s"Endpoint from config file address = ${conf.api.endpoint} port = ${conf.api.port}")
    val serverSource = Http(actorSystem).bind(interface = "127.0.0.1", port = 8080)

    val logRequest : HttpRequest => Unit = req => {
      log.info(s"================= ${req.method} REQUEST ${req.protocol.value} =============")
      log.info(s"uri : ${req.uri} ")
      log.info("  ---------- HEADER ---------")
      req.headers.zipWithIndex.foreach(hdr => log.info(s"   #${hdr._2} : ${hdr._1.toString}"))
      log.info("  ---------------------------")
      log.info(s"entity ${req.entity.toString} ")
      log.info("========================================================")
    }

    val reqHandler: HttpRequest => Future[HttpResponse] = {
      case request@HttpRequest(HttpMethods.GET, Uri.Path ("/"), httpHeader, requestEntity, requestProtocol)
      => logRequest(request)
        Future.successful {
        val resJson: Json = s"SimpleTestString ${request.uri}".asJson
        HttpResponse (
          StatusCodes.OK,
          entity = HttpEntity (`application/json`, Printer.noSpaces.print (resJson))
        )
      }
      case r: HttpRequest =>
        r.discardEntityBytes() // important to drain incoming HTTP Entity stream
        Future.successful{HttpResponse(404, entity = "Unknown resource!")}
    }


    val bindingFuture: Future[Http.ServerBinding] =
      serverSource.to(Sink.foreach { connection =>
        connection.handleWithAsyncHandler(reqHandler)
      }).run

    //bindingFuture
    Task(1)
    //return 1 if everything ok in processing request-response. Never return, because cycle nature.
    //or return Task(0)
  }



}