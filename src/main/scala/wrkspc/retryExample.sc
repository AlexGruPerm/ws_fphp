import zio._
import zio.console._

object MyApp extends App {
  def run(args: List[String]) =
    myAppLogic.fold(f => {println(s"result = fail ${f.getMessage}"); 1},
      s => {println(s"result = $s"); 0})

  def failedEffect :Task[Double] = Task(1/0)

  def effectAllTimeFail: Task[Double] =
    ZIO.fail(new Exception("exception message"))

  val myAppLogic =
    for {
      _    <- putStrLn("Begin")
      r = (new DefaultRuntime {}).unsafeRun(failedEffect)
      res <- effectAllTimeFail.retry(Schedule.recurs(3))
    } yield r+res
}

val runtime = new DefaultRuntime {}
runtime.unsafeRun(MyApp.run(List()))
