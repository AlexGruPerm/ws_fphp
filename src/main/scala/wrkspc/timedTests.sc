import zio._
import zio.clock._
import zio.random._
import zio.duration._
import zio._

object Timing extends App {

  val rndLimit: Int = 1000
  val replicaFactor: Int = 5

  val webRequest: ZIO[Clock with Random, Nothing, Int] =
    for {
      int <- zio.random.nextInt(rndLimit)
      _ <- ZIO.sleep(int.milliseconds)
    } yield int

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    for {
      results <- ZIO.collectAllPar(ZIO.replicate(replicaFactor)(webRequest.timed))
      times = results.map(_._1.toMillis)
      _ <- zio.console.putStrLn(s"times: $times, avg: ${times.sum / times.length}")
    } yield 0
  }
}

val runtime = Runtime.default
runtime.unsafeRun(Timing.run(List()))
