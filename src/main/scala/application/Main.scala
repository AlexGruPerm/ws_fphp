package application

import zio.App
import confs.Configuration
import envs.EnvContainer.ZEnvLog
import zio.logging._
import zio.{Task, UIO, ZEnv, ZIO}
import zio.console.putStrLn

object Main extends App {
  private def checkArgs : List[String] => ZIO[ZEnv,Throwable,Unit] = args => for {
    checkRes <- if (args.length < 0) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))}
    else {UIO.succeed(())
    }
  } yield checkRes

  private def wsApp: List[String] => ZIO[ZEnvLog, Throwable, Unit] = args =>
    for {
      _ <- logInfo("Web service starting")
      _ <- checkArgs(args)
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- WsServObj.WsServer(cfg)
      _ <- logInfo("Web service stopping!")
    } yield res

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    wsApp(args).provideCustomLayer(envs.EnvContainer.ZEnvLogLayer)
      .foldM(throwable => putStrLn(s"Error: ${throwable.getMessage}") *>
            ZIO.foreach(throwable.getStackTrace) { sTraceRow =>
              putStrLn(s"$sTraceRow")
            } as 1,
        _ =>  putStrLn(s"Success exit of application.") as 0
      )
}


//cfg <- Configuration.config.load("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/application.conf")
/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 * timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */