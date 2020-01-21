package confs

import java.io.File

import zio.RIO
import zio.Task
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
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
    val load: RIO[R, Config]//todo: remove it
    val loadFile: String => RIO[R, Either[ConfigReaderFailures, Config]]
  }

  val config: Service[Any] = new Service[Any] {
    val load: Task[Config] = Task.effect(ConfigSource.default.loadOrThrow[Config]) //todo: remove it
    val loadFile: String => Task[Either[ConfigReaderFailures, Config]] = confFileName =>
      Task.effect(ConfigSource.file(new File(confFileName))
        .load[Config]) //todo: replace on return type ZIO[Any,Throwable,Config] to eliminate fold in WsApp.
  }

}


