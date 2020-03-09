import zio._
import zio.console._
import zio.random._
import zio.duration._
import scala.concurrent._
import java.util.concurrent.Executors
import zio.internal.Executor
import zio.blocking._

object FirstExample extends App{

  val ectx1 = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(3))

  def asyncEffWithSleep: Task[Unit] =
    IO.effectAsync[Throwable,Unit](cb =>
      Thread.sleep(3000)((_, err) => {
        err match {
          case null => cb(IO.unit)
          case ex   => cb(IO.fail(ex))
        }
      })
        ()
    )

  def run(args :List[String]): ZIO[ZEnv,Nothing,Int] = {
    (for {
      _ <- putStrLn("1").lock(Executor.fromExecutionContext(3)(ectx1))
      f <- blocking(ZIO.sleep(3.seconds).zipRight(IO.effect(println("2")))).forkDaemon//.fork
      _ <- effectBlocking(println("3"))
      _ <- f.join
    } yield 0
    ) as 0
  } orElse(IO.succeed(1))
}

new DefaultRuntime {}.unsafeRun(FirstExample.run(List()))