package expressions.franz

import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import org.apache.avro.generic.{GenericData, GenericRecord}

import java.nio.ByteBuffer
import java.util.Base64
import scala.util.{Failure, Success, Try}

object JsonSupport {

  implicit object GenericRecordEncoder extends Encoder[GenericRecord] {
    override def apply(a: GenericRecord): Json = {
      val jason = GenericData.get.toString(a)
      parse(jason).toTry.get
    }
  }

  case class UnexpectedInput(input: Any) extends Exception(s"Unexpected input: ${Try(input.getClass)}: $input")

  /**
    * Functions for turning stuff into strings
    */
  object anyToString {
    val fromBytes = asBase64Text _

    lazy val format: Any => String = fold[String](fromBytes, identity[String], fromRecord, fromJson, fallback)

    def asBase64Text(bytes: Array[Byte]) = Base64.getEncoder.encodeToString(bytes)

    def fromJson(json: Json): String = json.asString.getOrElse(json.noSpaces)

    def fromRecord(record: GenericRecord): String = fromJson(GenericRecordEncoder(record))

    def fallback(other: Any): String = other match {
      case null => "NULL"
      case _    => other.toString
    }
  }

  // this probably won't be needed
  private def fromRecordBytes(bytes: Array[Byte]) = {
    val r: GenericRecord = ???
  }

  /**
    * Functions for turning stuff into json
    */
  object anyToJson {
    val format: Any => Json = fold[Json](fromBytes, fromString, fromRecord, identity[Json], fallback)

    def fromBytes(bytes: Array[Byte]): Json = {
//      val base64Str = anyToString.fromBytes(bytes)
      fromString(new String(bytes, "UTF-8"))
    }

    def fromString(jason: String): Json = io.circe.parser.parse(jason).toTry match {
      case Failure(_) =>
        // we could try and interpret the string as a base64 value - but let's not do that now.
        // we should instruct at construction time whether we want that behaviour as strings are a common/valid type
        // and performance would suck if we tried to guess every time
        //val bytes: Array[Byte] = Base64.getDecoder.decode(jason)
        Json.fromString(jason)
      case Success(j) => j
    }

    def fromRecord(record: GenericRecord): Json = GenericRecordEncoder(record)

    def fallback(other: Any): Json = other match {
      case null              => Json.Null
      case value: Int        => Json.fromInt(value)
      case value: Double     => Json.fromDoubleOrString(value)
      case value: Float      => Json.fromFloatOrString(value)
      case value: Long       => Json.fromLong(value)
      case value: Boolean    => Json.fromBoolean(value)
      case value: BigDecimal => Json.fromBigDecimal(value)
      case value: Map[String, Any] =>
        val entries: Seq[(String, Json)] = value.view.mapValues(format).toSeq
        Json.obj(entries: _*)
      case values: Iterable[_] =>
        Json.fromValues(values.map(anyToJson.format))
      case _ =>
        Json.obj(
          "error"  -> "JsonSupport.anyToJson.fallback can't unmarshall as json".asJson,
          "class"  -> other.getClass.toString.asJson,
          "object" -> other.toString.asJson
        )
    }
  }

  // format: off
  def fold[A](onBytes: Array[Byte] => A,
              onString: String => A,
              onRecord: GenericRecord => A,
              onJson: Json => A,
              onOther: Any => A) = { (input: Any) =>
    // format: on
    {
      input match {
        case value: ByteBuffer    => onBytes(value.array())
        case value: Array[Byte]   => onBytes(value)
        case value: GenericRecord => onRecord(value)
        case value: Json          => onJson(value)
        case value: CharSequence  => onString(value.toString)
        case other                => onOther(other)
      }
    }
  }
}
