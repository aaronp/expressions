package expressions.rest.server

import io.circe.parser.parse
import io.circe.{Encoder, Json}
import org.apache.avro.generic.{GenericData, GenericRecord}

object JsonSupport {

  implicit object GenericRecordEncoder extends Encoder[GenericRecord] {
    override def apply(a: GenericRecord): Json = {
      val jason = GenericData.get.toString(a)
      parse(jason).toTry.get
    }
  }

  def asString(json : Json): String = {
    json.asString.getOrElse(json.noSpaces)
  }
}
