package application

import java.io.{File, IOException}

import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/html`}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes, _}
import akka.stream.scaladsl.FileIO
import io.circe.Printer
import io.circe.syntax._
import logging.LoggerCommon._
import zio.console.putStrLn
import zio.logging.{LogLevel, log}
import zio.{IO, Ref, Task, UIO, ZEnv, ZIO}

import scala.concurrent.Future
import scala.io.{BufferedSource, Source}
import scala.language.postfixOps

object ReqResp {

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


  val logRequest : HttpRequest => Task[Unit] = request => for {
    _  <- zio.logging.locallyAnnotate(correlationId,"log_request"){
      for {
        _ <- log(LogLevel.Trace)(s"================= ${request.method} REQUEST ${request.protocol.value} =====")
        _ <- log(LogLevel.Trace)(s"uri : ${request.uri} ")
        _ <- log(LogLevel.Trace)("  ---------- HEADER ---------")
        _ <- ZIO.foreach(request.headers.zipWithIndex)(hdr => log(LogLevel.Trace)
        (s"   #${hdr._2} : ${hdr._1.toString}"))
        _ <- log(LogLevel.Trace)(s"  ---------------------------")
        _ <- log(LogLevel.Trace)(s"entity ${request.entity.toString} ")
        _ <- log(LogLevel.Trace)("========================================================")
      } yield ()
    }.provideSomeM(env)
  } yield ()


  val routPostTest: (HttpRequest,Ref[Int]) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache) => for {
      resJson <- Task{s"SimpleTestString ${request.uri}".asJson}
      _ <- logRequest(request)

      cvb <- cache.get
      _ <- putStrLn(s"BEFORE(test): cg=$cvb")
      _ <- cache.update(_ + 100)
      cva <- cache.get
      _ <- putStrLn(s"AFTER(test): cg=$cva")

      f <- ZIO.fromFuture { implicit ec =>
        Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, Printer.noSpaces.print(resJson))))
          .flatMap{result :HttpResponse => Future(result).map(_ => result)
          }
      }
    } yield f

  private def openFile(s: String): IO[IOException, BufferedSource] =
    IO.effect(Source.fromFile(s)).refineToOrDie[IOException]

  private def closeFile(f: BufferedSource): UIO[Unit] =
    UIO.unit

  //"/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/debug_post.html"
  val routeGetDebug: (HttpRequest, Ref[Int]) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache) => for {
    strDebugForm <- openFile("C:\\ws_fphp\\src\\main\\resources\\debug_post.html").bracket(closeFile) { file =>
      Task(file.getLines.mkString.replace("req_json_text", reqJsonText))
    }
    _ <- logRequest(request)
    cvb <- cache.get
    _ <- putStrLn(s"BEFORE(debug): cg=$cvb")
    _ <- cache.update(_ + 3)
    cva <- cache.get
    _ <- putStrLn(s"AFTER(debug): cg=$cva")

    f <- ZIO.fromFuture { implicit ec =>
      Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(`text/html` withCharset `UTF-8`, strDebugForm)))
        .flatMap {
          result: HttpResponse => Future(result).map(_ => result)
        }
    }
  } yield f


  val routeGetFavicon: HttpRequest => ZIO[ZEnv, Throwable, HttpResponse] = request => for {
    _ <- putStrLn(s"================= ${request.method} REQUEST ${request.protocol.value} =============")
    //icoFile <- Task{new File("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/favicon.png")}
    icoFile <- Task{new File("C:\\ws_fphp\\src\\main\\resources\\favicon.png")}
    f <- ZIO.fromFuture { implicit ec =>
      Future.successful(
        HttpResponse(StatusCodes.OK, entity =
          HttpEntity(MediaTypes.`application/octet-stream`, icoFile.length, FileIO.fromPath(icoFile.toPath))
        )
      ).flatMap{
          result :HttpResponse => Future(result).map(_ => result)
        }
    }
  } yield f


  val route404: HttpRequest => ZIO[ZEnv, Throwable, HttpResponse] = request => for {
    _ <- logRequest(request)
    f <- ZIO.fromFuture { implicit ec =>
      Future.successful(HttpResponse(404, entity = "Unknown resource!"))
        .flatMap{
          result :HttpResponse => Future(result).map(_ => result)
        }
    }
  } yield f


}