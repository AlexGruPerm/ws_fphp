package dbconn

import java.sql.{Connection, DriverManager, ResultSet, Statement}
import java.util.Properties

import dbconn.{PgConnectProp, PgSettings}
import org.slf4j.LoggerFactory
import zio.Task

case class pgSess(sess : Connection, pid : Int)

/**
 * Info:
 *
 * https://stackoverflow.com/questions/47651864/caused-by-org-postgresql-util-psqlexception-fatal-remaining-connection-slots
 *
 * Fiber failed.
 * A checked error was not handled.
 * org.postgresql.util.PSQLException: FATAL: remaining connection slots are reserved
 * or non-replication superuser connections
 *
 * in postgresql.conf I have this config :
 *
 * max_connections = 300
 * shared_buffers = 32GB
 *
 * maxPoolSize should be lower then max_connections
 *
 * --100
 * SELECT *
 * FROM   pg_settings
 * WHERE  name = 'max_connections';
 *
 * There are superuser_reserved_connections connections slots (3 by default) that are reserved for superusers so
 * that they can connect even in a situation where all connection slots are taken
 *
*/

trait jdbcSession {
  val logger = LoggerFactory.getLogger(getClass.getName)

  /**
   * Return Connection to Postgres or Exception
  */
  def createPgSess: (Int, PgConnectProp) => Task[pgSess] = (iterNum, cp) =>
    Task {
      //Class.forName(cp.driver)

      val url :String = cp.url
      val props = new Properties()
      props.setProperty("user",cp.username)
      props.setProperty("password",cp.password)

      //val c :Connection = DriverManager.getConnection(url, props)
      //-----------------------------------------------------------------------------------
      /*
      import com.jcraft.jsch.JSch
      val config = new java.util.Properties()
      config.put("StrictHostKeyChecking", "no")
      val session :com.jcraft.jsch.Session =(new JSch()).getSession("contractor", "212.33.224.13", 22008)
      session.setPassword("4rgpiqmsx13f")
      session.setConfig(config)
      session.connect()
      session.setPortForwardingL(6786, "172.17.100.53", 5432)

      //Class.forName(cp.driver)
      Class.forName(cp.driver).newInstance()
      props.setProperty("loglevel", "2")
      val c :Connection = DriverManager.getConnection("jdbc:postgresql://PRM-WS-0006.MOON.LAN:6786/db_ris_mkrpk", props)
      */
      //-----------------------------------------------------------------------------------
      val c :Connection = DriverManager.getConnection(cp.url, props)
      c.setClientInfo("ApplicationName",s"PgResearch-$iterNum")
      val stmt: Statement = c.createStatement
      val rs: ResultSet = stmt.executeQuery("SELECT pg_backend_pid() as pg_backend_pid")
      rs.next()
      val pg_backend_pid :Int = rs.getInt("pg_backend_pid")
      logger.info(s"User sesison pg_backend_pid = $pg_backend_pid")
      pgSess(c,pg_backend_pid)
    }

}

/**
 *  Singleton object that keep db connection.
*/
class PgConnection extends jdbcSession {

  //todo: read PgConnectProp properties single time from input json.
  val sess : (Int,PgConnectProp) => Task[pgSess] = (iterNum,conProp) =>
    createPgSess(iterNum,conProp)

  val getMaxConns : PgConnectProp => Task[PgSettings] = conProp =>
  for {
    pgSes :pgSess <- sess(0,conProp)
    maxConn <- Task{
      pgSes.sess.setAutoCommit(false)
      //setting as MAXCONN, SOURCEFILE
      val rs: ResultSet = pgSes.sess.createStatement.executeQuery(
        """ SELECT *
          | FROM   pg_settings
          | WHERE  name = 'max_connections' """.stripMargin)
      rs.next()
      val maxConn :Int = rs.getInt("setting")
      val srcConf :String = rs.getString("sourcefile")
      PgSettings(maxConn,srcConf)
    }
  } yield maxConn

}

