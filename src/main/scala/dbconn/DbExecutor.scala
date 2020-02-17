package dbconn

import confs.DbConfig
import data.DbErrorDesc
import reqdata.Dict
import io.circe.generic.JsonCodec, io.circe.syntax._

object DbExecutor {

  private val failJson = DbErrorDesc("error",
    "remaining connection slots are reserved for non-replication superuser connections",
    "Cause of exception",
    "PSQLException"
  ).asJson

  //each dict as Json result has a name f.e. "name" : "period"

  /*
 val execProcGetCursorData : (DbConfig,Dict) => = (conf, dict) => {

 }
  */


}
