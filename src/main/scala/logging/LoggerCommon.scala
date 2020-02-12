package logging

import zio.UIO
import zio.logging.{LogAnnotation, Logging}
import zio.logging.slf4j.Slf4jLogger

object LoggerCommon {

  val correlationId: LogAnnotation[String] = LogAnnotation[String](
    name = "correlationId",
    initialValue = "main-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logFormat = "[id = %s] %s"

  val env: UIO[Logging] = Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))

}
