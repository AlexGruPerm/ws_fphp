package application

import java.io.File

import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/html`}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes}
import io.circe.{Json, Printer}
import akka.Done
import zio.{DefaultRuntime, Managed, Ref, Schedule, Task, ZEnv, ZIO, console}
import akka.actor.{ActorSystem, _}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.util.Timeout
import confs.Config
import io.circe.syntax._
import io.circe.{Json, Printer}
import zio.console.putStrLn
import akka.http.scaladsl.model.HttpCharsets._

import scala.language.postfixOps
import scala.concurrent.Future
import scala.io.Source
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
import akka.stream.scaladsl.FileIO
import application.WsServObj.CommonTypes.IncConnSrvBind

import scala.concurrent.Future
import scala.language.postfixOps

object ReqResp {

  private val logRequest: (LoggingAdapter, HttpRequest) => Unit = (log, req) => {
    log.info(s"================= ${req.method} REQUEST ${req.protocol.value} =============")
    log.info(s"uri : ${req.uri} ")
    log.info("  ---------- HEADER ---------")
    req.headers.zipWithIndex.foreach(hdr => log.info(s"   #${hdr._2} : ${hdr._1.toString}"))
    log.info("  ---------------------------")
    log.info(s"entity ${req.entity.toString} ")
    log.info("========================================================")
  }

  private val reqJsonText =
    """
      |              {  "dicts": [
      |                {
      |                  "proc":"prm_salary.pkg_web_cons_rep_input_period_list(refcur => ?)"
      |                },
      |                  {
      |                    "proc":"prm_salary.pkg_web_cons_rep_grbs_list(refcur => ?, p_user_id => 45224506)"
      |                  },
      |                {
      |                  "proc":"prm_salary.pkg_web_cons_rep_institution_list(refcur => ?, p_user_id => 45224506)"
      |                },
      |                {
      |                  "proc":"prm_salary.pkg_web_cons_rep_form_type_list(refcur => ?)"
      |                },
      |                {
      |                  "proc":"prm_salary.pkg_web_cons_rep_territory_list(refcur => ?)"
      |                },
      |                {
      |                  "proc":"prm_salary.pkg_web_cons_rep_okved_list(refcur => ?)"
      |                }
      |              ]
      |             }
      |""".stripMargin


  val routPostTest: (HttpRequest,Ref[Int],LoggingAdapter) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache, log) => for {
      resJson <- Task{s"SimpleTestString ${request.uri}".asJson}
      _ <- putStrLn(s"================= ${request.method} REQUEST ${request.protocol.value} =============")
      _ <- putStrLn(s"uri : ${request.uri} ")
      _ <- putStrLn("  ---------- HEADER ---------")
      _ <- ZIO.foreach(request.headers.zipWithIndex)(hdr => console.putStrLn(s"   #${hdr._2} : ${hdr._1.toString}"))
      _ <- putStrLn("  ---------------------------")
      _ <- putStrLn(s"entity ${request.entity.toString} ")
      _ <- putStrLn("========================================================")

      cvb <- cache.get
      _ <- putStrLn(s"BEFORE(test): cg=${cvb}")
      _ <- cache.update(_ + 100)
      cva <- cache.get
      _ <- putStrLn(s"AFTER(test): cg=${cva}")

      f <- ZIO.fromFuture { implicit ec =>
        Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, Printer.noSpaces.print(resJson))))
          .flatMap{result :HttpResponse => Future(result).map(_ => result)
          }
      }
    } yield f


  val routeGetDebug: (HttpRequest,Ref[Int],LoggingAdapter) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache, log) => for {
      //strDebugForm <- Task{Source.fromFile("C:\\ws_fphp\\src\\main\\resources\\debug_post.html")
        strDebugForm <- Task{Source.fromFile("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/debug_post.html")
        .getLines.mkString.replace("req_json_text", reqJsonText)}
      _ <- putStrLn(s"================= ${request.method} REQUEST ${request.protocol.value} =============")
      _ <- putStrLn(s"uri : ${request.uri} ")
      _ <- putStrLn("  ---------- HEADER ---------")
      _ <- ZIO.foreach(request.headers.zipWithIndex)(hdr => console.putStrLn(s"   #${hdr._2} : ${hdr._1.toString}"))
      _ <- putStrLn("  ---------------------------")
      _ <- putStrLn(s"entity ${request.entity.toString} ")
      _ <- putStrLn("========================================================")
      cvb <- cache.get
      _ <- putStrLn(s"BEFORE(debug): cg=${cvb}")
      _ <- cache.update(_ + 3)
      cva <- cache.get
      _ <- putStrLn(s"AFTER(debug): cg=${cva}")

      f <- ZIO.fromFuture { implicit ec =>
        Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(`text/html` withCharset `UTF-8`, strDebugForm)))
          .flatMap{
            result :HttpResponse => Future(result).map(_ => result)
        }
      }
    } yield f

/*
todo: remove
  import MediaTypes._
  import akka.http.scaladsl.server.directives._
  import akka.http.impl._
  */

  val routeGetFavicon: (HttpRequest,Ref[Int],LoggingAdapter) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache, log) => for {
    _ <- putStrLn(s"================= ${request.method} REQUEST ${request.protocol.value} =============")
    fl <- Task{new File("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/favicon.png")}
    f <- ZIO.fromFuture { implicit ec =>
      Future.successful(
        HttpResponse(StatusCodes.OK, entity =
          HttpEntity(
            MediaTypes.`application/octet-stream`,
            fl.length,
            FileIO.fromPath(fl.toPath))
          //HttpEntity(`image/x-icon`, HttpData(new File("favicon.ico")))
        )
        //HttpResponse(StatusCodes.OK, entity = HttpEntity(`text/html` withCharset `UTF-8`, ""))
      )
        .flatMap{
          result :HttpResponse => Future(result).map(_ => result)
        }
    }
  } yield f


  val route404: (HttpRequest,Ref[Int],LoggingAdapter) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache, log) => for {
    _ <- putStrLn(s"====== 404 ====== ${request.method} REQUEST ${request.protocol.value} =============")
    _ <- putStrLn(s"uri : ${request.uri} ")
    _ <- putStrLn("  ---------- HEADER ---------")
    _ <- ZIO.foreach(request.headers.zipWithIndex)(hdr => console.putStrLn(s"   #${hdr._2} : ${hdr._1.toString}"))
    _ <- putStrLn("  ---------------------------")
    _ <- putStrLn(s"entity ${request.entity.toString} ")
    _ <- putStrLn("========================================================")
    f <- ZIO.fromFuture { implicit ec =>
      Future.successful(HttpResponse(404, entity = "Unknown resource!"))
        .flatMap{
          result :HttpResponse => Future(result).map(_ => result)
        }
    }
  } yield f






}
