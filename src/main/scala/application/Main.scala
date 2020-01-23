package application

import confs.{Config, Configuration}
import environments.env.{AppTaskRes, RunResType}
import org.slf4j.LoggerFactory
import pureconfig.error.ConfigReaderFailures
import zio.{Task, UIO, URIO}
import zio.console.{putStrLn}

/**
 * https://zio.dev/docs/overview/overview_index
 * Apache DBCP : https://devcenter.heroku.com/articles/database-connection-pooling-with-scala
 *  todo: add timout on effects  https://zio.dev/docs/overview/overview_basic_concurrency
 *    Timeout ZIO lets you timeout any effect using the ZIO#timeout method
 */
object Main extends zio.App {

  def run(args: List[String]): RunResType[Int] = {
    val logger = LoggerFactory.getLogger(getClass.getName)
    logger.info("Method run of Main")
    WsApp(args).foldM(
        throwable => putStrLn(s"Error: ${throwable.getMessage}") *> URIO.foreach(throwable.getStackTrace) {
          sTraceRow => putStrLn(s"$sTraceRow")
          }.map(_ => UIO.succeed(1)).flatten,
        _ => UIO.succeed(0)
      )
  }

  /**
   *
  */
  private val WsApp: List[String] => AppTaskRes[Int]  = args =>
    for {
      _ <- putStrLn("[1]Web service starting...")
      _ <- if (args.length < 0) Task.fail(new IllegalArgumentException("Need config file as parameter."))
      else UIO.succeed(()) //If args.length correct just return succeed effect
      conf: Either[ConfigReaderFailures, Config] <-
        Configuration.config.loadFile("C:\\ws_fphp\\src\\main\\resources\\application.conf")
      res <- conf.fold(
        FailConfig => {
          println(s"Can't load config file. Error ${FailConfig.toString}")
          UIO.succeed(0)
        },
        SuccessConf => {
          val dbConf = SuccessConf.dbConfig
          println(s"[2]Successful read config file. DB type = ${dbConf.dbtype} url = ${dbConf.url}")
          WsServer.WsServer(SuccessConf)
        }
      )
      _ <- putStrLn("[7] Web service stopping...")
    } yield res

}