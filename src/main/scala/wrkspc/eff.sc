import zio._
import zio.console._

object MyApp extends App {
  def run(args: List[String]) =
    myAppLogic.fold(f => {println(s"result = fail ${f.getMessage}"); 1},
      s => {println(s"result = $s"); 0})
/**
 * we can recover from this effects, but not from
 * val syncSideEffectX bcs.
 * effects are eager vals so they are being evaluated before even getting to the ZIO runtime
 * and the ZIO runtime can't recover from them.
  */
  def syncSideEff1: Double = 6 / 2
  def syncSideEff2: Double = 8 / 0

  val zioEff1: Task[Double] = ZIO.effect(syncSideEff1)
  val zioEff2: IO[ArithmeticException, Double] =
    ZIO.effect(syncSideEff2).refineToOrDie[ArithmeticException]

  val myAppLogic =
    for {
      _    <- putStrLn("Begin")
      d1 <- zioEff1
      d2 <- zioEff2.catchSome{case _ : ArithmeticException => zioEff1}
      _    <- putStrLn(s"End")
    } yield d1 + d2
}

val runtime = new DefaultRuntime {}
runtime.unsafeRun(MyApp.run(List()))
