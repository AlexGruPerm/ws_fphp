package dbconn

import java.io.IOException
import java.sql.Types
import java.util.concurrent.TimeUnit
import java.util.NoSuchElementException

import akka.util.ByteString
import confs.DbConfig
import data.{Cache, CacheEntity, DictDataRows, DictRow}
import io.circe.Printer
import org.postgresql.jdbc.PgResultSet
import io.circe.generic.auto._
import io.circe.syntax._
import logging.LoggerCommon.correlationId
import org.postgresql.util.PSQLException
import reqdata.Dict
import zio.clock.Clock
import zio.console.putStrLn
import zio.logging.{LogLevel, Logging, log}
import zio.{DefaultRuntime, Ref, Task, UIO, ZEnv, ZIO, clock}


//  loggetDict <- ZIO.access[Logging](_.logger)
//  _ <- loggetDict.locallyAnnotate(correlationId, "db_get_dict") {
//    log(LogLevel.Debug)(s"Connection opened for ${thisConfig.name} begin req ${trqDict.name}"/*conn.environment.connection.getClientInfo()*/)
//  }

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
        afterExecTs - beginTs,
        System.currentTimeMillis - afterExecTs,
        rows
    )
    )
  }

  import zio.blocking._
  val getDict: (List[DbConfig], Dict, Ref[Cache]) => ZIO[ZEnv,Throwable,DictDataRows] = (configuredDbList, trqDict, cache) =>
    for {
      thisConfig <- ZIO.fromOption(configuredDbList.find(dbc => dbc.name == trqDict.db))
          .mapError(_ => new NoSuchElementException(s"Database name [${trqDict.db}] not found in config."))
      tBeforeOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
      //todo: add Logging in env. and use it here with trace mode.
      //connections without pool.
      //thisConnection <- (new PgConnection).sess(thisConfig, trqDict.name)

      //connections with pool.
      //thisConnection <- pgPool.sess(thisConfig,trqDict)
      //todo: compare here, effect, effectBlocking or may be lock(ec)
      thisConnection <- effectBlocking(pgPool.sess(thisConfig,trqDict)).refineToOrDie[PSQLException]

      tAfterOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
      openConnDuration = tAfterOpenConn - tBeforeOpenConn
      //todo: try pass it direct (new PgConnection).sess(thisConfig)
      ds: DictDataRows <- getCursorData(tBeforeOpenConn, thisConnection, trqDict, openConnDuration)

      //if try take element from cache, if not exists than execute db query and save into cache.
      _ <- cache.update(cv => cv.copy(HeartbeatCounter = cv.HeartbeatCounter + 1,
        dictsMap = Map(0 -> CacheEntity(System.currentTimeMillis,ds)))
      )

      //we absolutely need close it to return to the pool
      _ = thisConnection.sess.close()
      //If this connection was obtained from a pooled data source, then it won't actually be closed, it'll just be returned to the pool.
    } yield ds

}


