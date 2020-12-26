package expressions.franz

import io.circe.Json
import org.apache.avro.generic.GenericRecord

/**
  * This is used to be able to create a particular type from a json input.
  *
  * It's used by the [[KafkaPublishService]] in order to shove in ProducerRecord's from user REST input
  * @tparam A
  */
sealed trait SupportedType[A] {
  def of(input: Json): A
}
object SupportedType {
  def keys(config: FranzConfig): SupportedType[_]   = config.keyType
  def values(config: FranzConfig): SupportedType[_] = config.valueType

  case object STRING extends SupportedType[String] {
    override def of(input: Json): String = input.asString.getOrElse(input.noSpaces)
  }
  case class RECORD(namespace: String) extends SupportedType[GenericRecord] {
    override def of(input: Json): GenericRecord = {
      val record = SchemaGen.recordForJson(input, namespace)

      record
    }
  }
  case object LONG extends SupportedType[Long] {
    override def of(input: Json) = input.asNumber.flatMap(_.toLong).getOrElse(sys.error(s"Couldn't convert >${input}< to a long"))
  }
  case object BYTE_ARRAY extends SupportedType[Array[Byte]] {
    override def of(input: Json) = {
      STRING.of(input).getBytes("UTF-8")
    }
  }
}
