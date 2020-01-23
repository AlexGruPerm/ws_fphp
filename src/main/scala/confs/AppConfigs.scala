package confs

final case class Config(api: ApiConfig, dbConfig: DbConfig)

final case class ApiConfig(endpoint: String, port: Int)

final case class DbConfig(dbtype: String,
                          driver: String,
                          url: String,
                          numThreads: Int,
                          user: String,
                          password: String)

