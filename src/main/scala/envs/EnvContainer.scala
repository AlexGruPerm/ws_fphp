package envs

import zio.{ZEnv, ZLayer}
import zio.clock.Clock
import zio.console.Console
import CacheZLayerObject._
import zio.logging.Logging
import zio.logging.Logging.Logging

object EnvContainer {
  type ZEnvLog = ZEnv with Logging
  type ZEnvLogCache =  ZEnvLog with CacheManager

   val env: ZLayer[Console with Clock, Nothing, Logging]   =
    Logging.console((_, logEntry) =>
      logEntry
    )

  val ZEnvLogLayer:  ZLayer[ZEnv, Nothing, ZEnvLog] = ZEnv.live ++ env

  val ZEnvLogCacheLayer: ZLayer[ZEnv, Nothing, ZEnvLogCache] =
    ZEnv.live ++ env /*++ ZEnv.live*/ ++ CacheManager.refCache

}
