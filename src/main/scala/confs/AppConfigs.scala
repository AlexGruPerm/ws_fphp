package confs

case class Config(api: ApiConfig, dbConfig: DbConfig)

case class ApiConfig(endpoint: String, port: Int)

case class DbConfig(driver: String,
                    url: String,
                    numThreads: Int,
                    user: String,
                    password: String)

