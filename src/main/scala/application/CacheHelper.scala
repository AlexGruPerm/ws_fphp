package application

import data.CacheEntity
import dbconn.PgConnection
import envs.CacheAsZLayer.CacheManager
import envs.DbConfig
import envs.EnvContainer.{ZEnvLog, ZEnvLogCache}
import org.postgresql.PGNotification
import zio.{Schedule, UIO, ZIO}
import zio.logging.log

object CacheHelper {

  /**
   *
   */
   val cacheChecker: ZIO[ZEnvLogCache, Nothing, Unit] =
    for {
      _ <- CacheLog.out("cacheChecker",true)
    } yield ()

  /**
   * Search Entity in Cache by tablename in  and remove it
   */
   private val removeFromCacheByRefTable: String => ZIO[ZEnvLogCache, Throwable, Unit] = tableName =>
    for {
      _ <- log.info(s"DB Listener PID = ")
      cache <- ZIO.access[CacheManager](_.get)
      cv <- cache.getCacheValue
      _ <- log.info(s"All keys = ${cv.dictsMap.keySet}")
      foundKeys: Seq[Int] = hashKeysForRemove(cv.dictsMap, tableName)
      _ <- log.info(s"keys for removing from cache $foundKeys")
      _ <- cache.remove(foundKeys)
    } yield ()


  val cacheCleaner: Array[PGNotification] => ZIO[ZEnvLogCache, Nothing, Unit] = notifications =>
    for {
      _ <- ZIO.foreach(notifications) { nt =>
        if (nt.getName == "change") {
          for {
            _ <- log.info(s"Notif: name = ${nt.getName} pid = ${nt.getPID} parameter = ${nt.getParameter}")
            _ <- removeFromCacheByRefTable(nt.getParameter)
          } yield UIO.succeed(())
        } else {
          UIO.succeed(())
        }
      }.catchAllCause {
        e => log.error(s" cacheValidator Exception $e")
      }
    } yield ()

  /**
   **
   *CREATE OR REPLACE FUNCTION notify_change() RETURNS TRIGGER AS $$
   * BEGIN
   * perform pg_notify('change', TG_TABLE_NAME);
   * RETURN NULL;
   * END;
   * $$ LANGUAGE plpgsql;
   **
   *drop TRIGGER trg_ch_listener_notify on listener_notify;
   **
   *create TRIGGER trg_ch_listener_notify
   * AFTER INSERT OR UPDATE OR DELETE ON listener_notify
   * FOR EACH statement EXECUTE PROCEDURE notify_change();
   *
   */
   val cacheValidator: (DbConfig, PgConnection) => ZIO[ZEnvLogCache, Throwable, Unit] = (conf, pgsessSrc) => {
    import zio.duration._
    for {
      pgsessLs <- pgsessSrc.sess orElse
        PgConnection(conf).sess.retry(Schedule.recurs(3) && Schedule.spaced(2.seconds))
      _ <- log.info(s"DB Listener PID = ${pgsessLs.pid}")
      notifications = scala.Option(pgsessLs.sess.getNotifications).getOrElse(Array[PGNotification]()) //timeout

      _ <- if (notifications.nonEmpty) {
        log.trace(s"notifications size = ${notifications.size}")
      } else {
        UIO.succeed()
      }
      _ <- cacheCleaner(notifications)
    } yield ()
  }

  /**
   * Is field reftables from Class CacheEntity contain given tableName
   */
  private def hashKeysForRemove(dictsMape: Map[Int, CacheEntity], tableName: String) :Seq[Int] =
    dictsMape.mapValues(v => v.reftables.contains(tableName)).withFilter(_._2).map(_._1).toSeq

}
