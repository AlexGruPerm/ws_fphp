package data

import io.circe.generic.JsonCodec
import io.circe.generic.auto._, io.circe.syntax._

@JsonCodec
case class DictRow(name: String, value: String)

/**
 * https://github.com/circe/circe/issues/892
 * if value has null as value, it can produce NPE
 *
 * scala> Foo.x
 * java.lang.NullPointerException
 * at io.circe.Printer$PrintingFolder.onString(Printer.scala:272)
 * at io.circe.Printer$PrintingFolder.onString(Printer.scala:256)
 * at io.circe.Json$JString.foldWith(Json.scala:299)
 * ...
 *
*/

object DictRow {
   def apply(name: String, value: String): DictRow =
    new DictRow(name, if (value==null) "null" else value)
}

@JsonCodec
case class DictDataRows(name: String,
                        connDurMs: Long,
                        execDurMs: Long,
                        fetchDurMs: Long,
                        rows: List[List[DictRow]])

@JsonCodec
case class DictsDataAccum(dicts: List[DictDataRows])


@JsonCodec
case class RequestResult(status: String,
                         data: DictsDataAccum)
//todo: maybe add prefix in reponse, is it from cache or not!?


