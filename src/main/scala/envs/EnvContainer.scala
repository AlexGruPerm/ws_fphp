package envs

import zio.{ZEnv, ZLayer}
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging
import CacheZLayerObject._

object EnvContainer {
  type ZEnvLog = ZEnv with Logging
  type ZEnvLogCache =  ZEnvLog with CacheManager

  private val env: ZLayer[Console with Clock, Nothing, Logging]   =
    Logging.console((_, logEntry) =>
      logEntry
    )

  val ZEnvLogLayer:  ZLayer[ZEnv, Nothing, ZEnvLog] = ZEnv.live ++ env

  val ZEnvLogCacheLayer: ZLayer[ZEnv, Nothing, ZEnvLogCache] =
    ZEnv.live ++ env ++ ZEnv.live ++ CacheManager.refCache


}
