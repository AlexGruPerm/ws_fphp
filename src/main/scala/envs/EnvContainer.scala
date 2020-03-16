package envs


import zio.ZEnv
import zio.ZLayer
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging

object EnvContainer {
  type ZEnvLog = ZEnv with Logging

  private val env: ZLayer[Console with Clock, Nothing, Logging]   =
    Logging.console((_, logEntry) =>
      logEntry
    )

  val ZEnvLogLayer:  ZLayer[ZEnv, Nothing, ZEnvLog]= ZEnv.live ++ env
}
