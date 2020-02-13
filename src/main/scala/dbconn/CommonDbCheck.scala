package dbconn

import zio.{Task, ZIO}
import zio.console.{Console, putStrLn}

object CommonDbCheck {

  /**
   *  Check that connect credentials are valid.
   */
  /*
  val checkDbConnectCredits : Task[DbConfig] => ZIO[Console, Throwable, Unit] = TdbConProps =>
    for {
      dbConProps <- TdbConProps
      pgSes: pgSess <- (new PgConnection).sess(0,dbConProps)
      _ <- putStrLn(s"Connection opened - ${!pgSes.sess.isClosed}")
    } yield ()
*/

  /**
   *  Get max_connections from pg config
   */
  /*
  val checkDbMaxConnections : Task[DbConfig] => ZIO[Console, Throwable, Unit] = TdbConProps =>
    for {
      dbConProps <- TdbConProps
      settings :PgSettings <- (new PgConnection).getMaxConns(dbConProps)
      _ <- putStrLn(s"Configuration: max_connections = ${settings.maxConn} conf : ${settings.sourceFile}")
    } yield ()

  */

}
