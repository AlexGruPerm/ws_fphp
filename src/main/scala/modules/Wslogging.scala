package modules

import zio.logging.slf4j.Slf4jLogger
import zio.logging.{LogAnnotation, LogLevel, Logging}
import zio.{Has, UIO, ZIO, ZLayer}

package object Wslogging {

  type Wslogger = Has[Wslogger.Service]

  object Wslogger{
    trait Service {
      def log: UIO[Logging]
    }

    val ZLayerLogger: ZLayer.NoDeps[Nothing, Wslogger] = ZLayer.succeed(
      new Service{
        def logFormat = "[id = %s] %s"
        def correlationId: LogAnnotation[String] = LogAnnotation[String](
          name = "correlationId",
          initialValue = "main-correlation-id",
          combine = (_, newValue) => newValue,
          render = identity
        )
        def log: UIO[Logging] =  Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))
      }
    )

    def out(logLevel :LogLevel)(s :String): ZIO[Wslogger, Nothing, Unit] =
      for {
        thisLogger <- ZIO.accessM[Wslogger](_.get.log)
        _ <- thisLogger.logger.log(logLevel)(s)
      } yield ()

  }
}
