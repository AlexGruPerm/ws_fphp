package dbconn

import akka.http.scaladsl.coding.Encoder
import confs.DbConfig
import data.DbErrorDesc
import reqdata.{Dict, RequestData}
import io.circe.generic.JsonCodec
import io.circe.syntax._
import zio.Task

object DbExecutor {

  private val failJson = DbErrorDesc("error",
    "remaining connection slots are reserved for non-replication superuser connections",
    "Cause of exception",
    "PSQLException"
  ).asJson

  //each dict as Json result has a name f.e. "name" : "period"
 /**
  * This function get parsed request data as Task[RequestData] - user_session and List of Dicts(name, db, proc).
  * Effectfully execute db queries in parallel (maybe on different db servers) and combine result into single
  * json String, to cover later in  HttpResponse(StatusCodes.OK, entity = HttpEntity( ...
  *
 */
 /*
 val execProcGetCursorData: Task[RequestData] => Task[String] = reqData =>
   for {

   } yield ???
*/


}
