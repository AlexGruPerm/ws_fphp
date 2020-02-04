package environments

import confs.Configuration
import zio.{ZEnv}

/**
 * Type aliases: https://zio.dev/docs/overview/overview_index
 *
 * RIO[R, A] — This is a type alias for ZIO[R, Throwable, A], which represents an effect that requires an R,
 * and may fail with a Throwable value, or succeed with an A.
 */
object env {
  type AppEnv = ZEnv with Configuration
}
