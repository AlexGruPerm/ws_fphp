package data

import akka.util.ByteString
import io.circe.generic.JsonCodec
import io.circe.generic.auto._
import io.circe.syntax._

@JsonCodec
case class DictRow(name: String, value: String)

/**
 * https://github.com/circe/circe/issues/892
 * if value has null as value, it can produce NPE
 *
 * scala> Foo.modules.logging.logging.x
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
//todo: maybe add prefix in response, is it from cache or not!?


case class CacheEntity(ts: Long, dictDataRows: DictDataRows, reftables: Seq[String])

object CacheEntity {
   def apply(data: DictDataRows, reftables: Seq[String]): CacheEntity =
    new CacheEntity(System.currentTimeMillis, data, reftables)
}

/**
 * class for cache entity instance.
 * Summary application cache contains List(CacheEntity)
 * One cache entity ~= one dictionary - DictDataRows
*/
case class Cache(HeartbeatCounter: Int, cacheCreatedTs: Long = System.currentTimeMillis, dictsMap: Map[Int, CacheEntity])
/*
object Cache{
   def apply(HeartbeatCounter: Int, dict: Map[Int, CacheEntity]): Cache =
    new Cache(HeartbeatCounter, dict)
}
*/



