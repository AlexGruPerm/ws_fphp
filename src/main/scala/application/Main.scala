package application

import confs.Configuration
import envs.EnvContainer
import modules.Wslogging.Wslogger
import zio.{Task, UIO, ZEnv, ZIO, ZLayer}
import zio.logging._

/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 * timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */

object Main extends zio.App {

  private def checkArgs : List[String] => ZIO[ZEnv,Throwable,Unit] = args => for {
    checkRes <- if (args.length < 0) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))}
    else {UIO.succeed(())
    }
  } yield checkRes

  private def WsApp: List[String] => ZIO[ZEnv with Wslogger, Throwable, Unit] = args =>
    for {
      _ <- Wslogger.out(LogLevel.Info)("Web service starting")
      _ <- checkArgs(args)
      //cfg <- Configuration.config.load("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/application.conf")
      //cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      cfg <- Configuration.config.load("C:\\PROJECTS\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- WsServObj.WsServer(cfg)
      _ <- Wslogger.out(LogLevel.Info)("Web service stopping")
    } yield res


  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args).provideCustomLayer(envs.EnvContainer.ZEnvLog).fold(_ => 1, _ => 0)

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
  /*
  _ <- checkArgs(args)
    //cfg <- Configuration.config.load("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/application.conf")
  cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
  res <- WsServObj.WsServer(cfg)
  */








//}

