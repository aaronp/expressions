package expressions.franz

import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.avro.generic.{GenericRecord, IndexedRecord}
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.kafka.consumer.CommittableRecord

import scala.util.Try

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

  def withKeyValue[K, V](record: CommittableRecord[_, _], key: K, value: V): CommittableRecord[K, V] = {
    val consumerRecord = withKeyValue(record.record, key, value)
    CommittableRecord(consumerRecord, record.offset)
  }

  /**
    * copy the consumer record, but use the given key/value
    * @return a mapped consumer record
    */
  def withKeyValue[K, V](record: ConsumerRecord[_, _], key: K, value: V): ConsumerRecord[K, V] = {
    new ConsumerRecord[K, V](
      record.topic(),
      record.partition(),
      record.offset(),
      record.timestamp(),
      record.timestampType(),
      ConsumerRecord.NULL_CHECKSUM,
      ConsumerRecord.NULL_SIZE,
      ConsumerRecord.NULL_SIZE,
      key,
      value,
      record.headers,
      record.leaderEpoch
    )
  }

  /**
    * Exposes a means to represent committable records as json values
    */
  object AsJson {

    /**
      * @param config the root configuration (which specifies the Serde types)
      * @return a function which can map/cast any incoming records as json records
      */
    def apply(config: FranzConfig): CommittableRecord[_, _] => CommittableRecord[Json, Json] = {
      val extractor = extractJson(config)
      (record: CommittableRecord[_, _]) =>
        {
          val (key, value) = extractor(record)
          withKeyValue(record, key, value)
        }
    }

    /**
      * @param config the configuration which contains the serde mapping
      * @return a function which extracts the json from the given record
      */
    def extractJson(config: FranzConfig) = {
      val jsonKey   = keyToJson(config.keyType)
      val jsonValue = valueToJson(config.valueType)
      (record: CommittableRecord[_, _]) =>
        {
          val key   = Try(jsonKey(record)).recover(e => asError(record, e, record.key, config.keyType)).get
          val value = Try(jsonValue(record)).recover(e => asError(record, e, record.value, config.valueType)).get
          (key, value)
        }
    }

    def asError(record: CommittableRecord[_, _], err: Throwable, value: Any, supportedType: SupportedType[_]) = {
      Json.obj(
        "error"         -> "SupportedType.AsJson threw an exception transforming record".asJson,
        "offset"        -> record.offset.offset.asJson,
        "partition"     -> record.partition.asJson,
        "topic"         -> record.record.topic().asJson,
        "key"           -> Option(record.key).getOrElse("null").toString.asJson,
        "valueType"     -> Try(value.getClass.toString).getOrElse("null").asJson,
        "valueString"   -> s"${value}".asJson,
        "error"         -> "Error transforming record".asJson,
        "supportedType" -> supportedType.toString.asJson,
        "exceptionMsg"  -> err.getMessage.asJson,
        "exception"     -> s"${err.toString}".asJson
      )
    }

    def keyToJson(supportedType: SupportedType[_]): CommittableRecord[_, _] => Json = supportedType match {
      case STRING =>
        (_: CommittableRecord[_, _]) match {
          case record: CommittableRecord[String, _] => record.key.asJson
          case record: CommittableRecord[_, _]      => JsonSupport.anyToJson.format(record.key)
        }
      case LONG =>
        (_: CommittableRecord[_, _]) match {
          case record: CommittableRecord[Long, _] => record.key.asJson
          case record: CommittableRecord[_, _]    => JsonSupport.anyToJson.format(record.key)
        }
      case RECORD(_) =>
        (_: CommittableRecord[_, _]) match {
          case record: CommittableRecord[IndexedRecord, _] => SchemaGen.asJson(record.key)
          case record: CommittableRecord[_, _]             => JsonSupport.anyToJson.format(record.key)
        }
      case BYTE_ARRAY =>
        (record: CommittableRecord[_, _]) =>
          JsonSupport.anyToJson.format(record.key)
    }
    def valueToJson(supportedType: SupportedType[_]): CommittableRecord[_, _] => Json = supportedType match {
      case STRING =>
        (_: CommittableRecord[_, _]) match {
          case record: CommittableRecord[_, String] => record.value.asJson
          case record: CommittableRecord[_, _]      => JsonSupport.anyToJson.format(record.value)
        }
      case LONG =>
        (_: CommittableRecord[_, _]) match {
          case record: CommittableRecord[_, Long] => record.value.asJson
          case record: CommittableRecord[_, _]    => JsonSupport.anyToJson.format(record.value)
        }
      case RECORD(_) =>
        (_: CommittableRecord[_, _]) match {
          case record: CommittableRecord[_, IndexedRecord] => SchemaGen.asJson(record.value)
          case record: CommittableRecord[_, _]             => JsonSupport.anyToJson.format(record.value)
        }
      case BYTE_ARRAY =>
        (record: CommittableRecord[_, _]) =>
          JsonSupport.anyToJson.format(record.value)
    }
  }
}
