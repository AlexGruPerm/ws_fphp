import zio.URIO
import zio.{ UIO, ZIO}
import zio.ZEnv
import zio.console.putStrLn
import zio.logging._
import zio.logging.slf4j.Slf4jLogger

object Main extends zio.App {
  val correlationId = LogAnnotation[String](
    name = "correlationId",
    initialValue = "undefined-correlation-id",
    combine = (_, newValue) => newValue,
    render = identity
  )
  val logFormat = "[correlation-id = %s] %s"

  val env = Slf4jLogger.make((context, message) => logFormat.format(context.get(correlationId), message))

  /*
  val env =
    Logging.console((_, logEntry) =>
      logEntry
    )
  */

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args)
      .foldM(
        throwable =>  putStrLn(s"Error: ${throwable.getMessage}") *> URIO.foreach(throwable.getStackTrace) {
          sTraceRow => putStrLn(s"$sTraceRow")
        }.map(_ => UIO.succeed(1)).flatten,
        _ => UIO.succeed(0)
      )

  private val WsApp: List[String] => ZIO[ZEnv, Throwable, Unit] = args =>
    for {
      _ <- putStrLn("begin")
      _ <- log("info message without correlation id").provideSomeM(env)
      _ <- putStrLn("end")
    } yield ()

}

