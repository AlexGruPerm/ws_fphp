package dbconn

import java.sql.Connection

import envs.DbConfig
import org.apache.commons.dbcp2.BasicDataSource
import reqdata.Dict

/**

select pid as process_id,
       usename as username,
       datname as database_name,
       client_addr as client_address,
       application_name,
       backend_start,
       state,
       state_change,
       wait_event,
       query
from pg_stat_activity
where coalesce(usename,'-') = 'prm_salary'
  and application_name like 'wsfphp%'
 order by application_name

*/
class PgConnectionPool() {

  println("Constructor PgConnectionPool")

  //todo: create personal pool with size from config for db-config.name and size.
  val dbUrl = s"jdbc:postgresql://172.17.100.53/db_ris_mkrpk"
  val connectionPool = new BasicDataSource()
  connectionPool.setUsername("prm_salary")
  connectionPool.setPassword("prm_salary")
  connectionPool.setDriverClassName("org.postgresql.Driver")
  connectionPool.setUrl(dbUrl)
  connectionPool.setDefaultAutoCommit(false)
  connectionPool.setMaxTotal(30)
   //connectionPool.setValidationQuery("select pg_backend_pid() as pg_backend_pid")
  connectionPool.setInitialSize(20)

  /*
  connectionPool.setRemoveAbandonedOnBorrow(true)
  connectionPool.setRemoveAbandonedTimeout(5)//Timeout in seconds before an abandoned connection can be removed.
  connectionPool.setLogAbandoned(true)
  */

  /**
   * The maximum number of milliseconds that the pool will wait (when there are no available connections)
   * for a connection to be returned before throwing an exception
  */
  connectionPool.setMaxWaitMillis(3000L)
  //https://commons.apache.org/proper/commons-dbcp/configuration.html

  def sess : (DbConfig,Dict) => pgSess/*Task[pgSess]*/ = (dbconf,trqDict) => {
    //Task.effect{
    val c: Connection = connectionPool /*dbconf.xxxxx*/ .getConnection
    c.setClientInfo("ApplicationName", s"wsfphp_${trqDict.name}")
    //c.setAutoCommit(false)
    pgSess(c, 0)
    //}.refineToOrDie[PSQLException]
  }

}
