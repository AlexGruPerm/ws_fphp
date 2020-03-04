import zio.{ Task, ZEnv, Runtime}
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{LogAnnotation, Logging}
import zio.{Has, UIO, ZIO, ZLayer}


object loggercommon{
  type Wslogger = Has[Wslogger.Service]

  object Wslogger{
    trait Service {
      def logFormat = "[id = %s] %s"
      def correlationId: LogAnnotation[String] = LogAnnotation[String](
        name = "correlationId",
        initialValue = "main-correlation-id",
        combine = (_, newValue) => newValue,
        render = identity
      )
      def log: UIO[Logging]
    }

    def ZLayerLogger: ZLayer.NoDeps[Nothing, Wslogger] = ZLayer.succeed(
      new Service{
        def log: UIO[Logging] =
          Slf4jLogger.make((context, message) =>
            logFormat.format(context.get(correlationId), message))
      }
    )

    def info(s :String): ZIO[Wslogger, Nothing, Unit] =
      for {
        thisLogger <- ZIO.accessM[Wslogger](_.get.log)
        _ <- thisLogger.logger.log(s)
      } yield ()

  }
}

import loggercommon._

object EnvContainer{
  def ZEnvLog: ZLayer.NoDeps[Nothing, ZEnv with Wslogger] =
    ZEnv.live ++ Wslogger.ZLayerLogger
}

object MyApp extends App {

  lazy val ZEnvLog: ZLayer.NoDeps[Nothing, ZEnv with Wslogger] =
    ZEnv.live ++ Wslogger.ZLayerLogger

  val WsApp: List[String] => ZIO[ZEnv with Wslogger, Throwable, Unit] = args =>
    for {
      _ <- Wslogger.info("Hello info.")
      res <- Task.unit
    } yield res

   def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args).provideCustomLayer(ZEnvLog).fold(_ => 1, _ => 0)

}

val runtime = Runtime.default
runtime.unsafeRun(MyApp.run(List()).provideLayer(EnvContainer.ZEnvLog))
