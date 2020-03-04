package application

import application.Main.ZEnvLog
import confs.Configuration
import logging.loggercommon
import zio.{Chunk, Task, UIO, URIO, ZEnv, ZIO, ZLayer}
import zio.logging._
import logging.loggercommon.Wslogger

/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 * timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */

object Main extends zio.App {

  val WsApp: List[String] => ZIO[ZEnv with Wslogger, Nothing, Unit] = args =>
    for {
      _ <- Wslogger.info("Hello info.")
      _ <- Wslogger.info("Hello debug.")
      res <- Task.unit
    } yield res

  val ZEnvLog: ZLayer.NoDeps[Nothing, ZEnv with Wslogger] =
    ZEnv.live ++ Wslogger.ZLayerLogger

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args).provideCustomLayer(ZEnvLog).fold(_ => 1, _ => 0)

}








  /*
  private val checkArgs : List[String] => Task[Unit] = args => for {
    checkRes <- if (args.length < 0) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))}
    else {UIO.succeed(())
    }
  } yield checkRes
*/


  /*
.foldM(
  throwable =>
    log(LogLevel.Error)(s"Error: ${throwable.getMessage}") *>
      URIO.foreach(throwable.getStackTrace) { sTraceRow =>
        log(LogLevel.Error)(s"$sTraceRow")
      } as 1,
  _ =>  log(LogLevel.Info)(s"Success exit of application.") as 0
)
*/
  /*
  _ <- checkArgs(args)
  cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
  res <- WsServObj.WsServer(cfg)
  */







  //cfg <- Configuration.config.load("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/application.conf")
//}

