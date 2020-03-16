package dbconn

import java.sql.Types
import java.util.concurrent.TimeUnit
import java.util.NoSuchElementException

import confs.DbConfig
import data.{ CacheEntity, DictDataRows, DictRow}
import envs.CacheZLayerObject.CacheManager
import envs.EnvContainer.{ZEnvLogCache}
import io.circe.Printer
import org.postgresql.jdbc.PgResultSet
import io.circe.generic.auto._
import io.circe.syntax._
import org.postgresql.util.PSQLException
import reqdata.Dict
import zio.logging.{logInfo}
import zio.{IO, Task, ZIO, clock}


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


/*  private def getValueFromCache(hashKey: Int, cache: Ref[Cache]) = // :ZIO[ZEnv,NoSuchElementException,DictDataRows] =
    cache.get.flatMap(dsCache =>
      IO.effect(dsCache.dictsMap.get(hashKey).map(ce => ce.dictDataRows).get))
      .refineToOrDie[NoSuchElementException]*/


/*  private def updateValueInCache(hashKey: Int, cache: Ref[Cache], ds: Task[DictDataRows],
                                 reftables: Option[Seq[String]]): Task[Unit] =
    for {
    dictRows <- ds
    _ <- cache.update(cv => cv.copy(HeartbeatCounter = cv.HeartbeatCounter + 1,
      dictsMap = cv.dictsMap + (hashKey -> CacheEntity(System.currentTimeMillis,dictRows, reftables.getOrElse(Seq()))))
    )
  } yield UIO.succeed(())*/



  import zio.blocking._
  private def getDictFromCursor: (DbConfig, Dict) => ZIO[ZEnvLogCache, Throwable, DictDataRows] =
    (configuredDb, trqDict) =>
      for {
        thisConfig <-
          if (configuredDb.name == trqDict.db) {
            Task(configuredDb)
          }
          else {
            IO.fail(new NoSuchElementException(s"Database name [${trqDict.db}] not found in config."))
          }
          //configuredDbList.find(dbc => dbc.name == trqDict.db)

          //.mapError(_ => new NoSuchElementException(s"Database name [${trqDict.db}] not found in config."))

        tBeforeOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
        //connections without pool.
        //thisConnection <- (new PgConnection).sess(thisConfig, trqDict.name)
        //connections with pool.
        //thisConnection <- pgPool.sess(thisConfig,trqDict)
        //todo: compare here, effect, effectBlocking or may be lock(ec)
        thisConnection <- effectBlocking(pgPool.sess(thisConfig, trqDict)).refineToOrDie[PSQLException]
        tAfterOpenConn <- clock.currentTime(TimeUnit.MILLISECONDS)
        openConnDuration = tAfterOpenConn - tBeforeOpenConn
        //todo: try pass it direct (new PgConnection).sess(thisConfig)
        dsCursor = getCursorData(tBeforeOpenConn, thisConnection, trqDict, openConnDuration)
        hashKey :Int = trqDict.hashCode() //todo: add user_session
        cache <- ZIO.access[CacheManager](_.get)
        dictRows <- dsCursor
        _ <- cache.set(hashKey, CacheEntity(System.currentTimeMillis, dictRows, trqDict.reftables.getOrElse(Seq())))
        //updateValueInCache(hashKey, cache, dsCursor, trqDict.reftables)
        ds <- dsCursor
        //we absolutely need close it to return to the pool
        _ = thisConnection.sess.close()
        //If this connection was obtained from a pooled data source, then it won't actually be closed, it'll just be returned to the pool.
      } yield ds


  val getDict: (DbConfig, Dict) => ZIO[ZEnvLogCache, Throwable, DictDataRows] =
    (configuredDb, trqDict) =>
      (for {
        cache <- ZIO.access[CacheManager](_.get)
        valFromCache <- cache.get(trqDict.hashCode())//getValueFromCache(trqDict.hashCode(), cache)
        _ <- logInfo(s">>>>>> value found in cache ${valFromCache.get} for hashKey=${trqDict.hashCode()}")
      } yield valFromCache.get.dictDataRows).foldM(
         _ => for {
          db <- getDictFromCursor(configuredDb, trqDict)
          _ <- logInfo(s"<<<<<< value get from db ${db.name}")
           //_ <- updateValueInCache(trqDict.hashCode(),cache,Task(db),trqDict.reftables)
          cache <- ZIO.access[CacheManager](_.get)
           _ <- cache.set(trqDict.hashCode(), CacheEntity(System.currentTimeMillis, db, trqDict.reftables.getOrElse(Seq())))
         } yield db,
        v  => ZIO.succeed(v)
  )


}


