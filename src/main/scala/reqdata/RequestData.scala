package reqdata

import io.circe.generic.JsonCodec

/**
 *  for one requested dict, like:
 * {
 * "db" : "db2_msk_gp",
 * "proc":"prm_salary.pkg_web_cons_rep_form_type_list(refcur => ?)"
 * }
 *
*/
@JsonCodec
case class Dict(name: String, db:String, proc: String)

/**
 * for all dicts requested and additional information.
*/
@JsonCodec
case class RequestData(user_session:String, dicts: Seq[Dict])




