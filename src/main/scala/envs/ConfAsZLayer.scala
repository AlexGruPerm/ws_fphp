package envs

import java.io.File
import java.util.Properties

import data.{Cache, CacheEntity, DictDataRows}
import pureconfig.ConfigSource
import zio.{Has, RIO, Tagged, Task, UIO, ZIO, ZLayer}
import pureconfig.ConfigSource
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._

final case class Config(api: ApiConfig, dbConfig: DbConfig, dbListenConfig :DbConfig)

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
  def urlWithDb: String = url + dbname

  def getJdbcProperties :Properties = {
    val props = new Properties()
    props.setProperty("user", username)
    props.setProperty("password", password)
    props
  }
}

object ConfAsZLayer {
  //#1
  type Configuration = zio.Has[Configuration.Service]

  //#2
  object Configuration {

    //#3
    trait Service {
      val load: String => Task[Config]
    }

    //#4
    trait wsConf extends Configuration.Service {
      override val load: String => Task[Config] = confFileName =>
        Task.effect(ConfigSource.file(new File(confFileName)).loadOrThrow[Config])
    }

    //#5
    def wsConf(implicit tag: Tagged[Configuration.Service]): ZLayer[Any, Nothing, Configuration] = {
      ZLayer.succeed(new wsConf {})
    }

  }

}
