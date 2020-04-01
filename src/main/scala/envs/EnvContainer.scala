package envs

import zio.{ZEnv, ZLayer}
import zio.clock.Clock
import zio.console.Console
import CacheAsZLayer._
import akka.http.scaladsl.Http.{IncomingConnection, ServerBinding}
import envs.ConfAsZLayer.Configuration
import zio.logging.Logging
import zio.logging.Logging.Logging

import scala.concurrent.Future


object EnvContainer {
  type IncConnSrvBind = akka.stream.scaladsl.Source[IncomingConnection, Future[ServerBinding]]

  type ZEnvLog = ZEnv with Logging
  type ZEnvLogCache =  ZEnvLog with CacheManager
  type ZEnvConfLogCache =  ZEnvLogCache with Configuration

   val env: ZLayer[Console with Clock, Nothing, Logging]   =
    Logging.console((_, logEntry) =>
      logEntry
    )

  val ZEnvLogLayer:  ZLayer[ZEnv, Nothing, ZEnvLog] = ZEnv.live ++ env

  val ZEnvLogCacheLayer: ZLayer[ZEnv, Nothing, ZEnvLogCache] =
    ZEnv.live ++ env ++ CacheManager.refCache

  val ZEnvConfLogCacheLayer: ZLayer[ZEnv, Nothing, ZEnvConfLogCache] =
    ZEnv.live ++ env ++ Configuration.wsConf ++ CacheManager.refCache

}
