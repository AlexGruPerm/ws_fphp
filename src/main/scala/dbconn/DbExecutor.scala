package dbconn

import java.sql.Types
import java.util.concurrent.TimeUnit
import java.util.NoSuchElementException

import application.CacheLog
import data.{CacheEntity, DictDataRows, DictRow}
import envs.CacheAsZLayer.CacheManager
import envs.DbConfig
import envs.EnvContainer.ZEnvLogCache
import org.postgresql.jdbc.PgResultSet
import org.postgresql.util.PSQLException
import reqdata.Dict
import zio.{IO, Task, ZIO, clock}
import zio.logging.log


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

  private def getDictFromCursor: (DbConfig, Dict) => ZIO[ZEnvLogCache, Throwable, DictDataRows] =
    (configuredDb, trqDict) =>
      for {
        cache <- ZIO.access[CacheManager](_.get)
        _ <- CacheLog.out("getDictFromCursor", false)
        thisConfig <-
          if (configuredDb.name == trqDict.db) {
            Task(configuredDb)
          }
          else {
            IO.fail(new NoSuchElementException(s"Database name [${trqDict.db}] not found in config."))
          }
        tBeforeOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
        thisConnection <- effectBlocking(pgPool.sess(thisConfig, trqDict)).refineToOrDie[PSQLException]
        tAfterOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
        openConnDuration = tAfterOpenConn - tBeforeOpenConn
        dsCursor = getCursorData(tBeforeOpenConn, thisConnection, trqDict, openConnDuration)
        hashKey: Int = trqDict.hashCode() //todo: add user_session
        dictRows <- dsCursor
        _ <- cache.set(hashKey, CacheEntity(System.currentTimeMillis, dictRows, trqDict.reftables.getOrElse(Seq())))
        ds <- dsCursor
        // We absolutely need close it to return to the pool
        _ = thisConnection.sess.close()
        // If this connection was obtained from a pooled data source, then it won't actually be closed,
        // it'll just be returned to the pool.
      } yield ds


  val getDict: (DbConfig, Dict) => ZIO[ZEnvLogCache, Throwable, DictDataRows] =
    (configuredDb, trqDict) =>
      for {
        cache <- ZIO.access[CacheManager](_.get)
        cv <- cache.getCacheValue
        _ <- log.trace(s"getDict HeartbeatCounter = ${cv.HeartbeatCounter} ")
        valFromCache: Option[CacheEntity] <- cache.get(trqDict.hashCode())
        dictRows <- valFromCache match {
          case Some(s: CacheEntity) =>
            log.trace(s"--- [VALUE GOT FROM CACHE] [${s.dictDataRows.name}] ---") *>
              ZIO.succeed(s.dictDataRows)
          case None => for {
            db <- getDictFromCursor(configuredDb, trqDict)
            _ <- log.trace(s"--- [VALUE GOT FROM DB] [${db.name}] ---")
          } yield db
        }
      } yield dictRows

}


