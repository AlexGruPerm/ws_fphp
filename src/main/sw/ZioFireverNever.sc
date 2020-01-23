import zio._
import zio.blocking.effectBlocking

/**
 * For example akka-http request-response cycle.
 */
def longExecutedImpureCode :Int = {
  println("before")
  Thread.sleep(5000)
  println("after")
  1
}

val interruptedEffect =
  for {
    fiber <- effectBlocking(longExecutedImpureCode).fork
    res     <- fiber.interrupt
  } yield res

val runtime = new DefaultRuntime {}
runtime.unsafeRun(interruptedEffect)
  .fold(f => {
    println(s"Fail $f")
    f.defects.foreach(println)
  },
    s=>{println("Success")}
  )