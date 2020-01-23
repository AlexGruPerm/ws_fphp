package application

import confs.{Config, Configuration}
import enironments.env.{AppTaskRes, RunResType}
import org.slf4j.LoggerFactory
import pureconfig.error.ConfigReaderFailures
import zio.console.{putStrLn}
import zio.{Task}

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
    WsApp(args).fold(
      f => {
        logger.error(s"Fail PgResearch.run f=$f msg=${f.getMessage} cause=${f.getCause}")
        f.getStackTrace.foreach(_ => logger.error(toString))
        0
      },
      s => {
        logger.info(s"Success. End web service. $s")
        1
      }
    )
  }

  /**
   * Read config file.
   * Start service as http server.
   * Close application when it's finished.
  */
  private val WsApp: List[String] => AppTaskRes[Int]  = args =>
    for {
      _ <- putStrLn("[1]Web service starting...")
      conf: Either[ConfigReaderFailures, Config] <-
        Configuration.config.loadFile("C:\\ws_fphp\\src\\main\\resources\\application.conf") //todo: args[0] how in ZIO?
      res <- conf.fold(
        FailConfig => {
          println(s"Can't load config file. Error ${FailConfig.toString}")
          Task(0)
        },
        SuccessConf => {
          val dbConf = SuccessConf.dbConfig
          println(s"[2]Successful read config file. DB type = ${dbConf.dbtype} url = ${dbConf.url}")
          application.WsServer(SuccessConf)
        }
      )//.forever.fork
      //
      //
      _ <- putStrLn("[7] Web service stopping...")
    } yield res

}