package confs

import java.io.File
import java.util.Properties

import zio.RIO
import zio.Task
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._

final case class Config(api: ApiConfig, dbConfig: DbConfig)

final case class ApiConfig(endpoint: String, port: Int)

final case class DbConfig(
                           name: String,
                           dbtype: String,
                           driver: String,
                           dbname: String,
                           url: String,
                           username: String,
                           password: String
                         ){
  def urlWithDb = url + dbname

  def getJdbcProperties :Properties = {
    val props = new Properties()
    props.setProperty("user", username)
    props.setProperty("password", password)
    props
  }
}

trait Configuration extends Serializable {
  val config: Configuration.Service[Any]
}

object Configuration {

  trait Service[R] {
    val load: String => RIO[R, Config]
  }

  /** Task.effect
   * * Imports a synchronous effect into a pure `ZIO` value, translating any
   * * throwables into a `Throwable` failure in the returned value.
  */
  val config: Service[Any] = new Service[Any] {
    val load: String => Task[Config] = confFileName =>
      Task.effect(ConfigSource.file(new File(confFileName)).loadOrThrow[Config])
  }
}



