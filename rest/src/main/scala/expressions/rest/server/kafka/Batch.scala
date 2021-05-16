package expressions.rest.server.kafka

import expressions.DynamicJson
import expressions.franz.{FranzConfig, KafkaRecord, SupportedType}
import expressions.rest.server.{JsonMsg, Topic}
import expressions.template.Message
import io.circe.syntax._
import io.circe.{Encoder, Json}
import zio.ZIO
import zio.kafka.consumer.CommittableRecord

import scala.collection.immutable.VectorBuilder

final case class Batch(topic: Topic, messages: Vector[Message[DynamicJson, DynamicJson]]) extends Iterable[Message[DynamicJson, DynamicJson]] {
  require(messages.forall(_.topic == topic))

  /**
    * Convenience method for ZIO.foreach...
    */
  def foreach[R, E, B](f: Message[DynamicJson, DynamicJson] => ZIO[R, E, B]): ZIO[R, E, Iterable[B]] = {
    ZIO.foreach(this)(f)
  }

  override def toString: String = {
    messages
      .sortBy(_.partition)
      .map { msg =>
        s"${msg.partition}:${msg.offset}"
      }
      .mkString(s"Batch for $topic of ${messages.length} : [", ", ", "]")
  }

  override def iterator: Iterator[Message[DynamicJson, DynamicJson]] = messages.iterator
}
object Batch {

  case class ByTopic(franzConfig: FranzConfig) {
    val AsMsg: CommittableRecord[_, _] => JsonMsg = SupportedType.AsJson(franzConfig).andThen(asMessage[Json, Json])

    def forRecords(records: IterableOnce[CommittableRecord[_, _]]): Iterable[Batch] = {
      val byTopic = collection.mutable.HashMap[String, VectorBuilder[JsonMsg]]()
      records.iterator.foreach { r =>
        val builder = byTopic.getOrElse(r.record.topic(), new VectorBuilder[JsonMsg]())
        builder += AsMsg(r)
      }
      byTopic.collect {
        case (topic, batch) => Batch(topic, batch.result())
      }
    }
  }

  def asMessage[K: Encoder, V: Encoder](record: CommittableRecord[K, V]): JsonMsg = {
    Message(
      DynamicJson(record.value.asJson),
      DynamicJson(record.key.asJson),
      record.timestamp,
      KafkaRecord.headerAsStrings(record),
      record.record.topic(),
      record.offset.offset,
      record.partition
    )
  }

}
