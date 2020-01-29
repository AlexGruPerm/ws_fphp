package environments

//import conf.Configuration
import confs.Configuration
import db.DbExecutor
import zio.{RIO, ZEnv, ZIO}

/**
 * Type aliases: https://zio.dev/docs/overview/overview_index
 *
 * RIO[R, A] — This is a type alias for ZIO[R, Throwable, A], which represents an effect that requires an R,
 * and may fail with a Throwable value, or succeed with an A.
 */
object env {

  type AppEnvironment = ZEnv

  type AppEnv = ZEnv
    with Configuration
  //with DbExecutor

  type AppTaskRes[A] = ZIO[AppEnvironment, Throwable, A]

}
