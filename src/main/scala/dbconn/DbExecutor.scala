package dbconn

import java.io.IOException
import java.sql.Types
import java.util.concurrent.TimeUnit
import java.util.NoSuchElementException

import confs.DbConfig
import data.{DictDataRows, DictRow}
import org.postgresql.jdbc.PgResultSet
import io.circe.generic.auto._
import io.circe.syntax._
import logging.LoggerCommon.correlationId
import reqdata.Dict
import zio.clock.Clock
import zio.console.putStrLn
import zio.logging.{LogLevel, Logging, log}
import zio.{DefaultRuntime, Task, UIO, ZEnv, ZIO, clock}


//  loggetDict <- ZIO.access[Logging](_.logger)
//  _ <- loggetDict.locallyAnnotate(correlationId, "db_get_dict") {
//    log(LogLevel.Debug)(s"Connection opened for ${thisConfig.name} begin req ${trqDict.name}"/*conn.environment.connection.getClientInfo()*/)
//  }

import zio.blocking._

object DbExecutor {

  val pgPool = new PgConnectionPool

  private def getCursorData(beginTs: Long, conn: pgSess,dict: Dict,openConnDur: Long) : Task[DictDataRows] = {
    val stmt = conn.sess.prepareCall(s"{call ${dict.proc} }")
    stmt.setNull(1, Types.OTHER)
    stmt.registerOutParameter(1, Types.OTHER)
    stmt.execute()
    val afterExecTs: Long = System.currentTimeMillis
    // org.postgresql.jdbc.PgResultSet
    //val refCur = stmt.getObject(1)
    val pgrs: PgResultSet = stmt.getObject(1).asInstanceOf[PgResultSet]
    val columns: List[(String, String)] = (1 to pgrs.getMetaData.getColumnCount)
      .map(cnum => (pgrs.getMetaData.getColumnName(cnum), pgrs.getMetaData.getColumnTypeName(cnum))).toList
    // here we itarate over all rows (PgResultSet) and for each row iterate over our columns,
    // and extract cells data with getString.
    //List[List[DictRow]]
    val rows = Iterator.continually(pgrs).takeWhile(_.next()).map { rs =>
      columns.map(cname => DictRow(cname._1, rs.getString(cname._1)))
    }.toList
    Task(
      DictDataRows(
        dict.name,
        openConnDur,
        (afterExecTs - beginTs),
        (System.currentTimeMillis - afterExecTs),
        rows
    )
    )
  }

  import zio.blocking._
  val getDict: (List[DbConfig], Dict) => ZIO[ZEnv,Throwable,DictDataRows] = (configuredDbList, trqDict) =>
    for {
      thisConfig <- ZIO.fromOption(configuredDbList.find(dbc => dbc.name == trqDict.db))
          .mapError(_ => new NoSuchElementException(s"Database name [${trqDict.db}] not found in config."))
      tBeforeOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
      //todo: add Logging in env. and use it here with trace mode.
      //connections without pool.
      //thisConnection <- (new PgConnection).sess(thisConfig, trqDict.name)
      //connections with pool.
      thisConnection <- pgPool.sess(thisConfig)
      //sess <- thisConnection.sess

      tAfterOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
      openConnDuration = tAfterOpenConn - tBeforeOpenConn
      ds: DictDataRows <- getCursorData(tBeforeOpenConn, thisConnection, trqDict, openConnDuration) //todo: try pass it direct (new PgConnection).sess(thisConfig)
      // _ = thisConnection.sess.close()
      // _ = thisConnection.sess.commit
      _ = thisConnection.sess.close() //If this connection was obtained from a pooled data source, then it won't actually be closed, it'll just be returned to the pool.
    } yield ds

}


