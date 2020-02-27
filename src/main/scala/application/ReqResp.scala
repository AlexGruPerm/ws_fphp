package application

import java.io.{File, IOException}
import java.sql.{Connection, Types}
import java.util.concurrent.TimeUnit
import java.util.{NoSuchElementException, Properties}

import akka.http.javadsl.model.headers.AcceptEncoding
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpCharsets._
import akka.http.scaladsl.model.MediaTypes.{`application/json`, `text/html`}
import akka.http.scaladsl.model.TransferEncodings.gzip
import akka.http.scaladsl.model.headers.`Content-Encoding`
import akka.http.scaladsl.model.{HttpEntity, HttpRequest, HttpResponse, StatusCodes, _}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.FileIO
import akka.util.ByteString
import confs.{Config, DbConfig}
import data.{CacheEntity, DbErrorDesc, DictDataRows, DictRow, DictsDataAccum, RequestResult}
import dbconn.{DbExecutor, PgConnection}
import io.circe.generic.JsonCodec
import io.circe.parser.parse
import io.circe.{Decoder, Encoder, Json, Printer}
import io.circe.syntax._
import logging.LoggerCommon.{env, _}
import zio.console.putStrLn
import zio.logging.{LogLevel, Logging, log}
import zio.{DefaultRuntime, IO, Ref, Schedule, Task, UIO, URIO, ZEnv, ZIO}

import scala.concurrent.{Await, Future}
import scala.io.{BufferedSource, Source}
import scala.language.postfixOps
import io.circe.syntax._
import org.postgresql.jdbc.PgResultSet
import reqdata.{Dict, NoConfigureDbInRequest, ReqParseException, RequestData}
import zio.clock.Clock
import io.circe.generic.JsonCodec
import testsjsons.CollectJsons

import scala.util.{Failure, Success, Try}


/**
 * monitor sessions from wsfphp:
 * select *
 * from   pg_stat_activity p
 * where  coalesce(p.usename,'-')='prm_salary'
 *   and application_name='wsfphp'
 *
*/
object ReqResp {

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


  val logReqData : Task[RequestData] => Task[Unit] = reqData => for {
    rd <- reqData
    _  <- zio.logging.locallyAnnotate(correlationId,"log_reqdata"){
      for {
        _ <- log(LogLevel.Trace)("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
        _ <- log(LogLevel.Trace)(s"session_id = ${rd.user_session}")
        _ <- log(LogLevel.Trace)(s"encoding_gzip = ${rd.cont_encoding_gzip_enabled}")
        _ <- URIO.foreach(rd.dicts)(d => log(LogLevel.Trace)
             (s"dict = ${d.db} - ${d.proc} "))
        _ <- log(LogLevel.Trace)("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
      } yield ()
    }.provideSomeM(env)
  } yield ()


/*
    private lazy val jdbcRuntime: DbConfig => zio.Runtime[JdbcIO] = dbconf => {
      val props = new Properties()
      props.setProperty("user", dbconf.username)
      props.setProperty("password", dbconf.password)
      zio.Runtime(new JdbcIO {
          Class.forName(dbconf.driver)
          //todo: maybe Properties instead of u,p
          val connection: Connection = java.sql.DriverManager.getConnection(dbconf.url + dbconf.dbname, props)
          connection.setClientInfo("ApplicationName", s"wsfphp")
          connection.setAutoCommit(false)
      }, zio.internal.PlatformLive.Default
      )
  }


  val getCursorData: Dict => ZIO[JdbcIO, Throwable, DictDataRows] = dict =>
    JdbcIO.effect { conn =>
      val stmt = conn.prepareCall(s"{call ${dict.proc} }")
      stmt.setNull(1, Types.OTHER)
      stmt.registerOutParameter(1, Types.OTHER)
      stmt.execute()
      // org.postgresql.jdbc.PgResultSet
      //val refCur = stmt.getObject(1)
      val pgrs : PgResultSet = stmt.getObject(1).asInstanceOf[PgResultSet]
      val columns: List[(String,String)] = (1 to pgrs.getMetaData.getColumnCount)
        .map(cnum => (pgrs.getMetaData.getColumnName(cnum),pgrs.getMetaData.getColumnTypeName(cnum))).toList
      // here we itarate over all rows (PgResultSet) and for each row iterate over our columns,
      // and extract cells data with getString.
      //List[List[DictRow]]
      DictDataRows(
        dict.name,
        0L,
        Iterator.continually(pgrs).takeWhile(_.next()).map { rs =>
          columns.map(cname => DictRow(cname._1, rs.getString(cname._1)))
        }.toList
      )
    }
  */

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
  import akka.http.scaladsl.coding.{Encoder, Gzip }
  import akka.http.scaladsl.model._, headers.HttpEncodings

  private def compress(usingGzip: Int, input: String) :ByteString =
    if (usingGzip==1) Gzip.encode(ByteString(input)) else ByteString(input)

  import io.circe.generic.auto._, io.circe.syntax._
  import scala.concurrent.duration._
  private val parseRequestData: Future[String] => Task[RequestData] = futString => {
    val strRequest: String  = Await.result(futString, 3 second)
    parse(strRequest) match {
      case Left (failure) => Task.fail (
        ReqParseException("Error code[001] Invalid json in request", failure.getCause)
        //new Exception (s"Invalid json in request, [code 001]. $failure")
      )
      case Right (json) => json.as[RequestData].swap
      match {
        case   Left(sq) => Task.succeed(sq)
        case   Right(failure) =>  Task.fail (
          ReqParseException("Error code[002] Invalid json in request", failure.getCause)
          //new Exception (s"Invalid json in request, [code 002]. $failure")
        )
      }
      }
    }

  /*
cvb <- cache.get
_ <- putStrLn(s"BEFORE(test): cg=$cvb")
_ <- cache.update(_ + 100)
cva <- cache.get
_ <- putStrLn(s"AFTER(test): cg=$cva")
*/

  /**
   * Function to check in one place that all dicts.db exist among configured db list (application.conf)
  */
  val dictDbsCheckInConfig: (Task[RequestData], List[DbConfig]) => ZIO[Logging, Throwable, Unit] =
    (requestData, configuredDbList) =>
      for {
        logChecker <- ZIO.access[Logging](_.logger)
        reqData <- requestData
        reqListDb: Seq[String] = reqData.dicts.map(_.db).distinct
        accumRes <-
          ZIO.foreachPar(reqListDb) { thisDb =>
            configuredDbList.find(_.name == thisDb) match {
              case Some(_) => ZIO.none
              case None => ZIO.some(s"DB [$thisDb] from request not found in config file application.conf")
            }
          }
        _ <- logChecker.log(LogLevel.Error)("error message here")
        _ <- logChecker.locallyAnnotate(correlationId, "db_conf_checker") {
          ZIO.foreach(accumRes.flatten)(thisErrLine => log(LogLevel.Error)(thisErrLine))
        }
        checkResult <- if (accumRes.flatten.nonEmpty) {
          Task.fail(
            NoConfigureDbInRequest(accumRes.flatten.head)
          )
        } else {UIO.succeed(())
        }
      } yield checkResult //UIO.succeed(())

  import zio.blocking._
  val routeDicts: (HttpRequest, Ref[CacheEntity], List[DbConfig], Future[String]) => ZIO[ZEnv, Throwable, HttpResponse] =
    (request, cache, configuredDbList, reqEntity) =>
      for {
        _ <- logRequest(request)
        reqRequestData = parseRequestData(reqEntity)
        _ <- logReqData(reqRequestData)
        seqResDicts <- reqRequestData
        //check that all requested db are configures.
        resString :ByteString <- dictDbsCheckInConfig(reqRequestData, configuredDbList).provideSomeM(env)
          .foldM(
            checkErr => {
              val failJson =
                DbErrorDesc("error", checkErr.getMessage, "Cause of exception", checkErr.getClass.getName).asJson
              Task(compress(seqResDicts.cont_encoding_gzip_enabled, Printer.spaces2.print(failJson)))
            },
            checkOk =>
              for {
              str <- ZIO.foreachPar(seqResDicts.dicts){ thisDict =>
                if (seqResDicts.thread_pool == "block") {
                  //run in separate blocking pool, "unlimited" thread count
                  blocking(DbExecutor.getDict(configuredDbList, thisDict, cache))
                } else {
                  //run on sync pool, count of threads equal CPU.cores*2 (cycle)
                  DbExecutor.getDict(configuredDbList, thisDict, cache)
                }
              }.fold(
                err =>  DbErrorDesc("error", err.getMessage, "method[routeDicts]", err.getClass.getName).asJson,
                succ => RequestResult("ok",DictsDataAccum(succ)).asJson
              )
            } yield compress(seqResDicts.cont_encoding_gzip_enabled, Printer.spaces2.print(str))
          )

        // Common logic
        resEntity <- Task(HttpEntity(`application/json`.withParams(Map("charset" -> "UTF-8")), resString))
        httpResp <- Task(HttpResponse(StatusCodes.OK, entity = resEntity))

        httpRespWithHeaders =
        if (seqResDicts.cont_encoding_gzip_enabled == 1) {
          httpResp.addHeader(`Content-Encoding`(HttpEncodings.gzip))
        } else {
          httpResp
        }

        resFromFuture <- ZIO.fromFuture { implicit ec =>
          Future.successful(httpRespWithHeaders).flatMap {
            result: HttpResponse => Future(result).map(_ => result)
          }
        }

    } yield resFromFuture






  private def openFile(s: String): IO[IOException, BufferedSource] =
    IO.effect(Source.fromFile(s)).refineToOrDie[IOException]

  private def closeFile(f: BufferedSource): UIO[Unit] =
    UIO.unit

  //"/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/debug_post.html"
  val routeGetDebug: (HttpRequest, Ref[CacheEntity]) => ZIO[ZEnv, Throwable, HttpResponse] = (request, cache) => for {
    strDebugForm <- openFile(
      "C:\\ws_fphp\\src\\main\\resources\\debug_post.html"
      //"C:\\PROJECTS\\ws_fphp\\src\\main\\resources\\debug_post.html"
    //"/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/debug_post.html"
    ).bracket(closeFile) { file =>
      Task(file.getLines.mkString.replace("req_json_text", CollectJsons.reqJsonText_))
    }
    _ <- logRequest(request)

    /*
    cvb <- cache.get
    _ <- putStrLn(s"BEFORE(debug): cg=$cvb")
    _ <- cache.update(_ + 3)
    cva <- cache.get
    _ <- putStrLn(s"AFTER(debug): cg=$cva")
    */

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