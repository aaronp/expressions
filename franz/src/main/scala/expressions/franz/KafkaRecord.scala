package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import org.apache.avro.generic.GenericData
import org.apache.kafka.common.header.Headers
import zio.kafka.consumer.{CommittableRecord, Offset}

import scala.util.Try

/** Our own representation of the deserialized data
  *
  * @param key        the record key
  * @param timestamp  the kafka record timestamp
  * @param offset     the commit offset
  * @param recordBody the deserialized message body
  */
final case class KafkaRecord[K, A](
    topic: String,
    key: K,
    timestamp: Long,
    offset: Offset,
    recordBody: A,
    headers: Map[String, String]
) {

  /** The generic record as json, combined with a 'kafka' json element
    *
    * @return the avro record as Json (it may fail, but if it did realistically that'd be a library bug)
    */
  def recordJson: Json = io.circe.parser.parse(recordJsonString).toTry.get

  def recordJsonString: String = GenericData.get.toString(recordBody)
}

object KafkaRecord extends StrictLogging {

//  type KafkaToRecord = CommittableRecord[String, Array[Byte]] => KafkaRecord[GenericData]

  def headerAsStrings(committableRecord: CommittableRecord[_, _]): Map[String, String] = {
    import scala.jdk.CollectionConverters._
    val headers: Headers = committableRecord.record.headers()
    val iterable = headers.asScala.flatMap { h =>
      Try(new String(h.value())).map(h.key -> _).toOption
    }
    iterable.toMap
  }

//  def decoder[K, V] : CommittableRecord[K, V] => ZIO[Any, Throwable, KafkaRecord[K,V]] = {
//    (committableRecord: CommittableRecord[K, V]) =>
//      {
//        val headers: Headers = committableRecord.record.headers()
//        import scala.jdk.CollectionConverters._
//        val headerAsStrings = headers.asScala.flatMap { h =>
//          Try(new String(h.value())).map(h.key -> _).toOption
//        }
////        serde.deserialize(committableRecord.record.topic(), headers, ).map { data =>
////        }
//          KafkaRecord[K,V](
//            committableRecord.record.topic(),
//            committableRecord.key,
//            committableRecord.timestamp,
//            committableRecord.offset,
//            committableRecord.value,
//            headerAsStrings.toMap
//          )
//      }
//  }
}
