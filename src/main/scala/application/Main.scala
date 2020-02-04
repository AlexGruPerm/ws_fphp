package application

import confs.{Config, Configuration}
import environments.env.{AppEnv}
import org.slf4j.LoggerFactory
import pureconfig.error.ConfigReaderFailures
import zio.{Task, UIO, URIO, ZIO}
import zio.ZEnv
import zio.console.{Console, putStrLn}

/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 *  todo: add timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */
object Main extends zio.App {

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] = {
    val logger = LoggerFactory.getLogger(getClass.getName)
    logger.info("Method run of Main")
    WsApp(args).foldM(
        throwable => putStrLn(s"Error: ${throwable.getMessage}") *> URIO.foreach(throwable.getStackTrace) {
          sTraceRow => putStrLn(s"$sTraceRow")
          }.map(_ => UIO.succeed(1)).flatten,
        _ => UIO.succeed(0)
      )
  }

  private val checkArgs : List[String] => Task[Unit] = args => for {
    checkRes <- if (args.length < 0) Task.fail(new IllegalArgumentException("Need config file as parameter."))
    else UIO.succeed(())
  } yield checkRes


  private val WsApp: List[String] => ZIO[ZEnv, Throwable, Unit]  = args =>
    for {
      _ <- putStrLn("[1]Web service starting...")
      _ <- checkArgs(args)

      /*
      confEnv <- ZIO.access[Configuration](env => env.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf"))//ZIO.environment[AppEnv]
      cfg <- confEnv
      */
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")

      res <- WsServObj.WsServer(cfg)
      _ <- putStrLn("[7] Web service stopping...")
    } yield res

}

