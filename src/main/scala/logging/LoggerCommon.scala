package logging

import zio.logging.LogAnnotation
import zio.logging.slf4j.Slf4jLogger

object LoggerCommon {

  val correlationId = LogAnnotation[String](
    name = "correlationId",
    initialValue = "main-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logFormat = "[id = %s] %s"

  val env = Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))

}
