package application

import confs.Configuration
import zio.URIO
import zio.{Task, UIO, ZIO}
import zio.ZEnv
import zio.logging._
import logging.LoggerCommon._

/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 * timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */

object Main extends zio.App {

  private val checkArgs : List[String] => Task[Unit] = args => for {
    checkRes <- if (args.length < 0) {
      Task.fail(new IllegalArgumentException("Need config file as parameter."))}
    else {UIO.succeed(())
    }
  } yield checkRes


  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args)
      .foldM(
        throwable =>
          log(LogLevel.Error)(s"Error: ${throwable.getMessage}").provideSomeM(env) *>
            URIO.foreach(throwable.getStackTrace) { sTraceRow =>
              log(LogLevel.Error)(s"$sTraceRow").provideSomeM(env)
            } as 1,
        _ =>  log(LogLevel.Info)(s"Success exit of application.").provideSomeM(env) as 0
      )

  private val WsApp: List[String] => ZIO[ZEnv, Throwable, Unit] = args =>
    for {
      _  <- zio.logging.locallyAnnotate(correlationId,"wsapp"){
         log(LogLevel.Info)("Web service starting")
        }.provideSomeM(env)
      _ <- checkArgs(args)
      cfg <- Configuration.config.load("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      //cfg <- Configuration.config.load("/home/gdev/data/home/data/PROJECTS/ws_fphp/src/main/resources/application.conf")
      res <- WsServObj.WsServer(cfg)
      _ <- log("Web service stopping").provideSomeM(env) //todo: remove it with external coverage of
    } yield res




}

