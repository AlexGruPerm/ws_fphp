package dbconn

import java.sql.{Connection, DriverManager, ResultSet, Statement}
import java.util.Properties

import envs.DbConfig
import org.postgresql.PGConnection
import org.postgresql.util.PSQLException
import zio.Task

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

/**
 *  Singleton object that keep db connection.
*/
//todo: what if make it object ???
case class PgConnection(dbconf : DbConfig){

  println("Constructor PgConnection")
  private val c :Connection = DriverManager.getConnection(dbconf.urlWithDb, dbconf.getJdbcProperties)
  c.setClientInfo("ApplicationName",s"wsfphp_notif_listener")
  c.setAutoCommit(true)
  private val pgconn = c.asInstanceOf[PGConnection]
  private val stmt: Statement = c.createStatement
  private val rs: ResultSet = stmt.executeQuery("SELECT pg_backend_pid() as pg_backend_pid")
  rs.next()
  private val pg_backend_pid :Int = rs.getInt("pg_backend_pid")
  rs.close()
  stmt.close()

  //good example: http://www.smartjava.org/content/listen-notifications-postgresql-scala/
  private val stmtListen = c.createStatement
  stmtListen.execute("LISTEN change")

  //todo: read PgConnectProp properties single time from input json.
  def sess : Task[pgSessListen] = Task {
      pgSessListen(c,pgconn,pg_backend_pid)
    }.refineToOrDie[PSQLException]

}

