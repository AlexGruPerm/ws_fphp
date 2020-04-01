package application

import envs.CacheAsZLayer.CacheManager
import envs.EnvContainer.ZEnvLogCache
import zio._
import zio.logging.log
import scala.language.postfixOps

object CacheLog {
  val out: (String, Boolean) => ZIO[ZEnvLogCache, Nothing, Unit] = (effName, incrHeadrbeat) =>
    for {
      cache <- ZIO.access[CacheManager](_.get)
      cv <- cache.getCacheValue
      _ <- log.trace(s"[$effName] HeartbeatCounter = ${cv.HeartbeatCounter} " +
        s"bornTs = ${cv.cacheCreatedTs} dictsMap.size = ${cv.dictsMap.size}")
      _ <- if (incrHeadrbeat) {
        cache.addHeartbeat
      } else {
        ZIO.succeed(())
      }
    } yield ()
}
