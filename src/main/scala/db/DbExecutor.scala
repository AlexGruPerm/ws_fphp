package db

  import java.sql.{Connection, DriverManager}

  import data.DbDataTypes.DictRow
  import zio._

  trait DbExecutor {
    val dbExecutor: DbExecutor.Service
  }

  object DbExecutor {
    trait Service{
      // dependencies
      //val dbConf: Configuration.Service[Config]

       private val db = /*for {
       ???
         dbConf <- dbConf.load.map(_.dbConfig)
         conn = DriverManager.getConnection(dbConf.url, dbConf.props.getProperties)

       } yield*/ ZIO.effectTotal(1/*conn*/)

       val getDict : Int => Task[DictRow] = id => Task(Map("FIELD1"->"VALUE1","FIELD2"->"VALUE2"))
    }
  }
