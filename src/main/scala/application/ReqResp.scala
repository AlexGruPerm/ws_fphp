package application

import java.io.{File, IOException}
import java.sql.Connection
import java.util.{NoSuchElementException, Properties}

import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/html`}
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes, _}
import akka.stream.scaladsl.FileIO
import confs.{Config, DbConfig}
import data.DbErrorDesc
import dbconn.JdbcIO
import io.circe.Printer
import io.circe.syntax._
import logging.LoggerCommon._
import zio.console.putStrLn
import zio.logging.{LogLevel, log}
import zio.{IO, Ref, Schedule, Task, UIO, URIO, ZEnv, ZIO}

import scala.concurrent.Future
import scala.io.{BufferedSource, Source}
import scala.language.postfixOps
import io.circe.generic.JsonCodec
import io.circe.syntax._


/**
 * monitor sessions from wsfphp:
 * select *
 * from   pg_stat_activity p
 * where  coalesce(p.usename,'-')='prm_salary'
 *   and application_name='wsfphp'
 *
*/
object ReqResp {

  private val reqJsonText =
    """
      |              { "user_session" : "9d6iQk5LmtfpoYd78mmuHsajjaI2rbRh",
      |                "dicts": [
      |                {
      |                  "db" : "db1_msk_gu",
      |                  "proc":"prm_salary.pkg_web_cons_rep_input_period_list(refcur => ?)"
      |                },
      |                  {
      |                    "db" : "db2_msk_gp",
      |                    "proc":"prm_salary.pkg_web_cons_rep_grbs_list(refcur => ?, p_user_id => 45224506)"
      |                  },
      |                {
      |                  "db" : "db1_msk_gu",
      |                  "proc":"prm_salary.pkg_web_cons_rep_institution_list(refcur => ?, p_user_id => 45224506)"
      |                },
      |                {
      |                  "db" : "db2_msk_gp",
      |                  "proc":"prm_salary.pkg_web_cons_rep_form_type_list(refcur => ?)"
      |                },
      |                {
      |                  "db" : "db1_msk_gu",
      |                  "proc":"prm_salary.pkg_web_cons_rep_territory_list(refcur => ?)"
      |                },
      |                {
      |                  "db" : "db2_msk_gp",
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


    private lazy val jdbcRuntime: DbConfig => zio.Runtime[JdbcIO] = dbconf => {
    val props = new Properties()
    props.setProperty("user", dbconf.username)
    props.setProperty("password", dbconf.password)
    zio.Runtime(new JdbcIO {
      Class.forName(dbconf.driver)
      //todo: maybe Properties instead of u,p
      val connection: Connection = java.sql.DriverManager.getConnection(dbconf.url + dbconf.dbname, props)
      connection.setClientInfo("ApplicationName",s"wsfphp")
    }, zio.internal.PlatformLive.Default
    )
  }

  /*
  private lazy val jdbcCloseRuntime: zio.Runtime[JdbcIO] => ZIO[JdbcIO,Nothing,Unit] = zrt => for {
    _ <- zrt.environment.closeConnection
  } yield ()
  */


  /*
  private val closeConnection: URIO[zio.Runtime[JdbcIO],Unit] =
    for {
     rtJdbc <- ZIO.environment[JdbcIO] // connJdbcIO.environment.connection.close()
     _ = rtJdbc.connection.close()
    } yield UIO.succeed(())
  */

  val getEntries: ZIO[JdbcIO, Throwable, List[(String, String)]] =
    JdbcIO.effect { c =>
      val q = "SELECT NAME, MESSAGE FROM GUESTBOOK ORDER BY ID ASC"
      val stmt = c.createStatement
      val rs = stmt.executeQuery(q)

      //fetch rows from opened cursor
      def _entries(acc: List[(String, String)]): List[(String, String)] =
        if (rs.next()) {
          val entry = (rs.getString("NAME"), rs.getString("MESSAGE"))
          _entries(entry :: acc)
        } else {
          stmt.close
          acc
        }

      _entries(Nil)
    }


  val closeConn: ZIO[JdbcIO, Throwable, Unit] =
    JdbcIO.effect { c =>
      c.close()
    }

  /**
   * This is one of client handler.
   * Inside it opens 1-* database connections for parallel executing queries.
   * Sometimes it can be Exception:
   * org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved for
   * non-replication superuser connection
   * And we need catch it and return response json to client like this :
   * {
   *  "status" : "error",
   *  "message" : "remaining connection slots are reserved for non-replication superuser connections",
   *  "exception class" : "PSQLException"
   * }
  */
  val routPostTest: (HttpRequest,Ref[Int],List[DbConfig]) => ZIO[ZEnv, Throwable, HttpResponse] =
    (request, cache, dbConfigList) => for {
      resJson <- Task{s"SimpleTestString ${request.uri}".asJson}
      _ <- logRequest(request)

      dbConnName :String = "db1_msk_gu"
      dbCFG : DbConfig = dbConfigList.find(dbc => dbc.name == dbConnName)
        .fold(
          throw new NoSuchElementException(s"There is no this db connection name [$dbConnName] in config file."))(
          s => s)

      failJson = DbErrorDesc("error",
        "remaining connection slots are reserved for non-replication superuser connections",
        "Cause of exception",
        "PSQLException"
      ).asJson

/*
      cvb <- cache.get
      _ <- putStrLn(s"BEFORE(test): cg=$cvb")
      _ <- cache.update(_ + 100)
      cva <- cache.get
      _ <- putStrLn(s"AFTER(test): cg=$cva")
*/

      httpResp <- Task{jdbcRuntime(dbCFG)}
        .fold(
          failConn => HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, Printer.noSpaces.print(failJson))),
          succConn => {
            //here we need execute effect that use jdbcRuntime for execute queries in db.
            //and then close explicitly close connection. todo: adbcp - don't close.
            succConn.environment.closeConnection
            HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, Printer.noSpaces.print(resJson)))
          }
        )

      resFromFuture <- ZIO.fromFuture { implicit ec => Future.successful(httpResp).flatMap{
        result: HttpResponse => Future(result).map(_ => result)
      }}

    } yield resFromFuture


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