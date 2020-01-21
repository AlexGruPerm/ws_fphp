package application

import java.io.File

import confs.{Config, Configuration}
import enironments.env.{AppTaskRes, RunResType}
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import zio.clock.Clock
import zio.console.{Console, putStrLn}
import zio.{Task, ZIO}

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
      _ <- putStrLn("Web service starting...")
      //read and parse config and send as env into WsServer
      conf     <- Configuration.config.load
      _ <- putStrLn(s"Config loaded : url = ${conf.dbConfig.url}")
      //wsresult <- application.WsServer(conf)
      /*
      conf :Either[ConfigReaderFailures, Config] = ConfigSource.file(new File("C:\\ws_fphp\\src\\main\\resources\\application.conf")).load[Config]
      res <- conf match {
        case Left(err) => {
          println("LEFT")
          Task(0)}
        case Right(cf :Config) => {
          println("Right")
          application.WsServer(cf)
        }
      }
      */


      _ <- putStrLn("Web service stopping...")
      res <- Task(1)
    } yield res



}