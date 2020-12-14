package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax._
import org.apache.avro.generic.{GenericData, GenericRecord}
import zio.ZIO
import zio.kafka.consumer.{CommittableRecord, Offset}
import zio.kafka.serde.Deserializer

/** Our own representation of the deserialized data
  *
  * @param key        the record key
  * @param timestamp  the kafka record timestamp
  * @param offset     the commit offset
  * @param recordBody the deserialized message body
  */
final case class KafkaRecord(
    key: String,
    timestamp: Long,
    offset: Offset,
    recordBody: GenericRecord
) {

  /** The generic record as json, combined with a 'kafka' json element
    *
    * @return the avro record as Json (it may fail, but if it did realistically that'd be a library bug)
    */
  def recordJson = io.circe.parser.parse(recordJsonString).toTry.map(_.deepMerge(kafkaJson))

  def kafkaJson = Json.obj(
    "kafka" -> Json.obj(
      "key"       -> key.asJson,
      "timestamp" -> timestamp.asJson,
      "offset"    -> offset.offset.asJson,
      "topic"     -> offset.topicPartition.topic().asJson,
      "partition" -> offset.topicPartition.partition().asJson
    )
  )

  def recordJsonString = GenericData.get.toString(recordBody)
}

object KafkaRecord extends StrictLogging {

  type KafkaToRecord = CommittableRecord[String, Array[Byte]] => KafkaRecord

  def decoder(serde: Deserializer[Any, GenericRecord]): CommittableRecord[String, Array[Byte]] => ZIO[Any, Throwable, KafkaRecord] = {
    (committableRecord: CommittableRecord[String, Array[Byte]]) =>
      serde.deserialize(committableRecord.record.topic(), committableRecord.record.headers(), committableRecord.value).map { data =>
        KafkaRecord(
          committableRecord.key,
          committableRecord.timestamp,
          committableRecord.offset,
          data
        )
      }
  }
}
