package data

import akka.util.ByteString
import io.circe.Encoder
import io.circe.generic.JsonCodec

object ResDataTypes {

  implicit val byteStringEncoder =
    Encoder.encodeString.contramap[ByteString](_.utf8String)

  case class DictsDataAccumBs(dicts: List[ByteString])

}
