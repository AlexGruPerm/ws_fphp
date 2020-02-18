package data

import io.circe.generic.JsonCodec
//import io.circe.generic.JsonCodec, io.circe.syntax._

/**
 * describe db error for responding to client as json.
*/
@JsonCodec case class DbErrorDesc(
                                   status: String,
                                   message: String,
                                   cause: String,
                                   exception: String)

