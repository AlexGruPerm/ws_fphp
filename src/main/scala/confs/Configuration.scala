package confs

import java.io.File

import zio.RIO
import zio.Task
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._

trait Configuration extends Serializable {
  val config: Configuration.Service[Any]
}

object Configuration {

  trait Service[R] {
    val load: String => RIO[R, Config]
    val loadFile: String => RIO[R, Either[ConfigReaderFailures, Config]]
  }

  /** Task.effect
   * * Imports a synchronous effect into a pure `ZIO` value, translating any
   * * throwables into a `Throwable` failure in the returned value.
  */
  val config: Service[Any] = new Service[Any] {

    val load: String => Task[Config] = confFileName =>
      Task.effect(ConfigSource.file(new File(confFileName)).loadOrThrow[Config])

    val loadFile: String => Task[Either[ConfigReaderFailures, Config]] = confFileName =>
      Task.effect(ConfigSource.file(new File(confFileName)).load[Config])

  }



}

final case class Config(api: ApiConfig, dbConfig: DbConfig)

final case class ApiConfig(endpoint: String, port: Int)

final case class DbConfig(dbtype: String,
                          driver: String,
                          url: String,
                          numThreads: Int,
                          user: String,
                          password: String)
