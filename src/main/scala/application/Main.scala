package application

import confs.Configuration
import modules.Wslogging.Wslogger
import zio.{Task, UIO, ZEnv, ZIO}
import zio.logging.LogLevel
import zio.logging._
import zio.console.putStrLn

/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 * timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */

object Main extends zio.App {

  //val logFormat = "[correlation-id = %s] %s"

  private def checkArgs : List[String] => ZIO[ZEnv,Throwable,Unit] = args => for {
    checkRes <- if (args.length < 0) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))}
    else {UIO.succeed(())
    }
  } yield checkRes

  private def wsApp: List[String] => ZIO[ZEnv with Wslogger, Throwable, Unit] = args =>
    for {
      _ <- Wslogger.ZLayerLogger.map(_.get.log) >>> log(LogLevel.Info)("xcxc") // >>> log(LogLevel.Info)("Web service starting")
      _ <- checkArgs(args)
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- WsServObj.WsServer(cfg)
      //_ <- Wslogger.out(LogLevel.Info)("Web service stopping")
    } yield res

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    wsApp(args).provideCustomLayer(envs.EnvContainer.ZEnvLog)
      .foldM(throwable => putStrLn(s"Error: ${throwable.getMessage}") *>
            ZIO.foreach(throwable.getStackTrace) { sTraceRow =>
              putStrLn(s"$sTraceRow")
            } as 1,
        _ =>  putStrLn(s"Success exit of application.") as 0
      )

}








  /*

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

