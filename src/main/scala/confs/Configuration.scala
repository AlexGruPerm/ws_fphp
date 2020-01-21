package confs

import zio.RIO
import zio.Task
import pureconfig.ConfigSource
import pureconfig.generic.auto._
/*
import zio.RIO
import zio.Task
import pureconfig.ConfigSource
import pureconfig.generic.auto._
*/

trait Configuration extends Serializable {
  val config: Configuration.Service[Any]
}

object Configuration {
  trait Service[R] {
    val load: RIO[R, Config]
  }

  val config: Service[Any] = new Service[Any] {
    val load: Task[Config] = Task.effect(ConfigSource.default.loadOrThrow[Config])
  }

}


