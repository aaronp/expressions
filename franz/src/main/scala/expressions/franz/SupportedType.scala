package expressions.franz

import io.circe.Json
import org.apache.avro.generic.GenericRecord

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
