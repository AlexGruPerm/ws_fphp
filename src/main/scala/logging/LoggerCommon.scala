package logging

import akka.event.Logging.LogLevel
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{LogAnnotation, LogLevel, Logging}
import zio.{Has, UIO, ZIO, ZLayer}

/**
 * Wslogger - module
*/
package object loggercommon{

  type Wslogger = Has[Wslogger.Service]

  object Wslogger{
    trait Service {
      val logFormat = "[id = %s] %s"
      val correlationId: LogAnnotation[String] = LogAnnotation[String](
        name = "correlationId",
        initialValue = "main-correlation-id",
        combine = (_, newValue) => newValue,
        render = identity
      )
      def log: UIO[Logging]
    }

    val ZLayerLogger: ZLayer.NoDeps[Nothing, Wslogger] = ZLayer.succeed(
      new Service{
        def log: UIO[Logging] =  Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))
      }
    )

    def info(s :String): ZIO[Wslogger, Nothing, Unit] =
      for {
        thisLogger <- ZIO.accessM[Wslogger](_.get.log)
        _ <- thisLogger.logger.log(s)
      } yield UIO.unit

    def debug(s :String): ZIO[Wslogger, Nothing, Unit] =
      for {
        thisLogger <- ZIO.accessM[Wslogger](_.get.log)
        _ <- thisLogger.logger.log(s)
      } yield UIO.unit

  }
}



/*
object LoggerCommon {

  val logFormat = "[id = %s] %s"
  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "main-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )


  val env: UIO[Logging] = Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))
}
*/