package modules

import zio.logging.slf4j.Slf4jLogger
import zio.logging.{LogAnnotation, LogLevel, Logging}
import zio.{Has, Task, ZIO, ZLayer}

package object Wslogging {

  type Wslogger = Has[Wslogger.Service]

  object Wslogger{
    trait Service {
      def log:  ZLayer[Any,Nothing,Logging]
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
        //0.2.2. def log: UIO[Logging] =  Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))
        //0.2.3
        def log: ZLayer[Any,Nothing,Logging] =
          Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))
      }
    )

    val live  = ZIO.access[Wslogger](_.get.log)
  //  def out(logLevel :LogLevel)(s :String):  ZLayer[Any,Nothing,Logging] =
  //    for {
  //      thisLogger <- ZIO.accessM[Wslogger](_.get.log)
  //    } yield thisLogger

  }
}
