import zio._
import zio.console._

object MyApp extends App {
  def run(args: List[String]) =
    myAppLogic.fold(f => {println(s"result = fail ${f.getMessage}"); 1},
      s => {println(s"result = $s"); 0})

  val myAppLogic =
    for {
      _    <- putStrLn("Begin")
      r <- Task(1/2.toDouble)
      _    <- putStrLn(s"End")
    } yield r
}

val runtime = new DefaultRuntime {}
runtime.unsafeRun(MyApp.run(List()))