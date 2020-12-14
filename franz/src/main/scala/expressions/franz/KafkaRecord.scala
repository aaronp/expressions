package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax._
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.kafka.common.serialization.Deserializer
import zio.kafka.consumer.{CommittableRecord, Offset}

import scala.util.Try

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
    recordBody: Try[GenericRecord]
) {

  /** The generic record as json, combined with a 'kafka' json element
    *
    * @return the avro record as Json (it may fail, but if it did realistically that'd be a library bug)
    */
  def recordJson: Try[Json] = recordJsonString.flatMap { json =>
    io.circe.parser.parse(json).toTry.map(_.deepMerge(kafkaJson))
  }

  def kafkaJson = Json.obj(
    "kafka" -> Json.obj(
      "key"       -> key.asJson,
      "timestamp" -> timestamp.asJson,
      "offset"    -> offset.offset.asJson,
      "topic"     -> offset.topicPartition.topic().asJson,
      "partition" -> offset.topicPartition.partition().asJson
    )
  )

  def recordJsonString: Try[String] = recordBody.map { avro =>
    GenericData.get.toString(avro)
  }
}

object KafkaRecord extends StrictLogging {

  type KafkaToRecord = CommittableRecord[String, Array[Byte]] => KafkaRecord

  def decoder(topic: String, serde: Deserializer[GenericRecord]): KafkaToRecord = { (committableRecord: CommittableRecord[String, Array[Byte]]) =>
    {
      val data: Try[GenericRecord] = Try(serde.deserialize(topic, committableRecord.value))
      KafkaRecord(
        committableRecord.key,
        committableRecord.timestamp,
        committableRecord.offset,
        data
      )
    }
  }
}
