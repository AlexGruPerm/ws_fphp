package application

import zio.{App, Layer, Runtime, Task, UIO, ZEnv, ZIO}
import confs.Configuration
import envs.EnvContainer.{ZEnvLog, ZEnvLogCache}
import zio.logging._
import zio.console.putStrLn

object Main extends App {

  private def checkArgs : List[String] => ZIO[ZEnv,Throwable,Unit] = args => for {
    checkRes <- if (args.length < 0) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))}
    else {UIO.succeed(())
    }
  } yield checkRes



  private def wsApp: (List[String],Runtime.Managed[ZEnvLogCache]) => ZIO[ZEnvLogCache, Throwable, Unit] = (args,rt) =>
    for {
      _ <- logInfo("Web service starting")
      _ <- checkArgs(args)
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- WsServObj.WsServer(cfg, rt)
      _ <- logInfo("Web service stopping!")
    } yield res

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val appLayer: Layer[Nothing, ZEnvLogCache] = ZEnv.live >>> envs.EnvContainer.ZEnvLogCacheLayer
    val rt: Runtime.Managed[ZEnvLogCache]  = Runtime.unsafeFromLayer(appLayer)
    rt.unsafeRun(wsApp(args,rt))
    ZIO(rt.unsafeRun(wsApp(args,rt))).foldM(throwable => putStrLn(s"Error: ${throwable.getMessage}") *>
      ZIO.foreach(throwable.getStackTrace) { sTraceRow =>
        putStrLn(s"$sTraceRow")
      } as 1,
      _ =>  putStrLn(s"Success exit of application.") as 0
    )
  }


}


//cfg <- Configuration.config.load("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/application.conf")
/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 * timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */