package envs

import modules.Wslogging.Wslogger
import zio.ZEnv
import zio.ZLayer

object EnvContainer {
  val ZEnvLog: ZLayer.NoDeps[Nothing, ZEnv with Wslogger] =
    ZEnv.live ++ Wslogger.ZLayerLogger
}
