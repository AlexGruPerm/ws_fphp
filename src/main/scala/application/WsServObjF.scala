package application

import akka.Done
import zio.{Managed, Ref, Schedule, Task, UIO, ZEnv, ZIO}
import akka.actor.{ActorSystem, _}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl._
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.model.{HttpRequest, _}
import akka.stream.scaladsl.{Source, _}
import akka.util.Timeout
import confs.Config
import io.circe.syntax._
import io.circe.{Json, Printer}
import zio.console.putStrLn

import scala.io.Source
import akka.http.scaladsl.model.HttpCharsets._

import scala.concurrent.Future
import scala.language.postfixOps


object WsServObjF {

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

  //ex of using Ref for cache. https://stackoverflow.com/questions/57252919/scala-zio-ref-datatype


  // val cache :UIO[Ref[Int]] = Ref.make(0)

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
    val ActSys = ActorSystem("WsDb")
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
          fiber <- startRequestHandler(cache, conf, actorSystem).fork //with this way we can build up more akka-http servers.
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


  ///////////////       1
  val serverSource: ActorSystem => ZIO[Any, Throwable, akka.stream.scaladsl.Source[Http.IncomingConnection, Future[ServerBinding]]] = actorSystem => Task(
    Http(actorSystem).bind(interface = "127.0.0.1", port = 8080)
  )

  /////////////////     2
  import scala.io.Source
  val reqHandlerM: (ActorSystem, HttpRequest) => Future[HttpResponse]/*ZIO[Any, Throwable, HttpRequest => Future[HttpResponse]]*/ =  (actorSystem, request) => {
    implicit val system = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext = system.dispatcher

    val log = Logging(system,"WsDb")

    val rr = request match {
      case request@HttpRequest(HttpMethods.POST, Uri.Path("/test"), httpHeader, requestEntity, requestProtocol)
      => logRequest(log, request)
        Future.successful {
          val resJson: Json = s"SimpleTestString ${request.uri}".asJson
          HttpResponse(
            StatusCodes.OK,
            entity = HttpEntity(`application/json`, Printer.noSpaces.print(resJson))
          )
        }
      case request@HttpRequest(HttpMethods.GET, Uri.Path("/debug"), httpHeader, requestEntity, requestProtocol)
      => logRequest(log, request)
        log.info(httpHeader.mkString(";"))
        Future.successful {
          val resJson: Json = s"SimpleTestString ${request.uri}".asJson
          val strDebugForm: String = Source.fromFile("C:\\ws_fphp\\src\\main\\resources\\debug_post.html").getLines
            .mkString
            .replace("req_json_text", reqJsonText)
          HttpResponse(
            StatusCodes.OK,
            entity = HttpEntity(`text/html` withCharset `UTF-8`, strDebugForm)
          )
        }
      case request: HttpRequest =>
        logRequest(log, request)
        request.discardEntityBytes() // important to drain incoming HTTP Entity stream
        Future.successful {
          HttpResponse(404, entity = "Unknown resource!")
        }
    }


     rr
  }


  val startRequestHandler: (Ref[Int], Config, ActorSystem) => ZIO[Any, Throwable, Future[Done]] = (cache, conf, actorSystem) => {
    implicit val system = actorSystem
    import scala.concurrent.duration._
    implicit val timeout: Timeout = Timeout(10 seconds)
    implicit val executionContext = system.dispatcher
    for {
      serverSource <- serverSource(actorSystem)
      srvReqHdlr <-
          Task{serverSource.runForeach { connection =>
            connection.handleWithAsyncHandler(req => reqHandlerM(actorSystem, req)) //handler: HttpRequest => Future[HttpResponse]
          }}
    } yield srvReqHdlr

  }

}

