package application

import zio.{App, Layer, Runtime, Task, UIO, ZEnv, ZIO}
import confs.Configuration
import envs.EnvContainer.{ZEnvConfLogCache, ZEnvConfLogCacheLayer}
import zio.logging._
import zio.console.putStrLn

object Main extends App {

  private def checkArgs: List[String] => ZIO[ZEnv, Throwable, Unit] = args => for {
    checkRes <- if (args.size<0/*args.isEmpty*/) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))
    }
    else {
      UIO.succeed(())
    }
  } yield checkRes

  private def wsApp: List[String] => ZIO[ZEnvConfLogCache, Throwable, Unit] = args =>
    for {
      _ <- log.info("Web service starting")
      _ <- checkArgs(args)
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      //configService <- ZIO.access[Configuration](_.config)
      //cfg = configService.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- WebService.startService(cfg)
      _ <- log.info("Web service stopping")
    } yield res

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val appLayer: Layer[Nothing, ZEnvConfLogCache] = ZEnv.live >>> envs.EnvContainer.ZEnvConfLogCacheLayer
    val rt: Runtime.Managed[ZEnvConfLogCache] = Runtime.unsafeFromLayer(appLayer)
    ZIO(rt.unsafeRun(wsApp(args))).foldM(throwable => putStrLn(s"Error: ${throwable.getMessage}") *>
      ZIO.foreach(throwable.getStackTrace) { sTraceRow =>
        putStrLn(s"$sTraceRow")
      } as 1,
      _ => putStrLn(s"Success exit of application.") as 0
    )
  }

}