package dbconn

import java.sql.{Connection, Types}
import java.util.concurrent.TimeUnit
import java.util.{NoSuchElementException, Properties}

import confs.DbConfig
import data.{DictDataRows, DictRow}
import org.postgresql.jdbc.PgResultSet
import io.circe.generic.auto._
import io.circe.syntax._
import logging.LoggerCommon.correlationId
import reqdata.{Dict, NoConfigureDbInRequest}
import zio.clock.Clock
import zio.logging.{LogLevel, Logging, log}
import zio.{DefaultRuntime, Task, ZIO}

object DbExecutor {

  private lazy val jdbcRuntime: DbConfig => zio.Runtime[JdbcIO] = dbconf => {
    val props = new Properties()
    props.setProperty("user", dbconf.username)
    props.setProperty("password", dbconf.password)
    zio.Runtime(new JdbcIO {
      Class.forName(dbconf.driver)
      //todo: maybe Properties instead of u,p
      val connection: Connection = java.sql.DriverManager.getConnection(dbconf.url + dbconf.dbname, props)
      connection.setClientInfo("ApplicationName", s"wsfphp")
      connection.setAutoCommit(false)
    }, zio.internal.PlatformLive.Default
    )
  }

  private lazy val getCursorData: Dict => ZIO[JdbcIO, Throwable, DictDataRows] = dict =>
    JdbcIO.effect { conn =>
      val stmt = conn.prepareCall(s"{call ${dict.proc} }")
      stmt.setNull(1, Types.OTHER)
      stmt.registerOutParameter(1, Types.OTHER)
      stmt.execute()
      // org.postgresql.jdbc.PgResultSet
      //val refCur = stmt.getObject(1)
      val pgrs : PgResultSet = stmt.getObject(1).asInstanceOf[PgResultSet]
      val columns: List[(String,String)] = (1 to pgrs.getMetaData.getColumnCount)
        .map(cnum => (pgrs.getMetaData.getColumnName(cnum),pgrs.getMetaData.getColumnTypeName(cnum))).toList
      // here we itarate over all rows (PgResultSet) and for each row iterate over our columns,
      // and extract cells data with getString.
      //List[List[DictRow]]
      DictDataRows(
        dict.name,
        0L,
        Iterator.continually(pgrs).takeWhile(_.next()).map { rs =>
          columns.map(cname => DictRow(cname._1, rs.getString(cname._1)))
        }.toList
      )
    }

  def currentTime: ZIO[Clock, Nothing, Long] = ZIO.accessM[Clock](_.clock.currentTime(TimeUnit.MILLISECONDS))

  //  loggetDict <- ZIO.access[Logging](_.logger)
  //  _ <- loggetDict.locallyAnnotate(correlationId, "db_get_dict") {
  //    log(LogLevel.Debug)(s"Connection opened for ${thisConfig.name} begin req ${trqDict.name}"/*conn.environment.connection.getClientInfo()*/)
  //  }

  val getDict: (List[DbConfig], Dict) => Task[DictDataRows] = (configuredDbList, trqDict) =>
    for {
      thisConfig <- ZIO.fromOption(configuredDbList.find(dbc => dbc.name == trqDict.db))
          .mapError(_ => new NoSuchElementException(s"Database name [${trqDict.db}] not found in config."))
      ds: DictDataRows = jdbcRuntime(thisConfig).unsafeRun(
        JdbcIO.transact(
          getCursorData(trqDict)
        )
      )
    } yield ds


}


