import logging.loggercommon.Wslogger
import zio.{ Task, ZEnv, ZIO, ZLayer}

object MyApp extends App {

  val ZEnvLog: ZLayer.NoDeps[Nothing, ZEnv with Wslogger] =
    ZEnv.live ++ Wslogger.ZLayerLogger

  val WsApp: List[String] => ZIO[ZEnv with Wslogger, Nothing, Unit] = args =>
    for {
      _ <- Wslogger.info("Hello info.")
      _ <- Wslogger.debug("Hello debug.")
      res <- Task.unit
    } yield res

  override def run(args: List[String]): ZIO[ZEnv, Nothing, Int] =
    WsApp(args).provideCustomLayer(ZEnvLog).fold(_ => 1, _ => 0)

}

val runtime = new zioDefaultRuntime {}
runtime.unsafeRun(MyApp.run(List()))
