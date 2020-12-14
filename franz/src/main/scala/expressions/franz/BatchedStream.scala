package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import zio._
import zio.duration.Duration
import zio.kafka.consumer.{Consumer, ConsumerSettings, OffsetBatch, Subscription}
import zio.kafka.serde.Serde

/** A Kafka stream which will batch up records by the least of either a time-window or max-size,
  * and then use the provided 'persist' function on each batch
  */
object BatchedStream extends StrictLogging {

  type JsonString = String

  /** @param config our parsed typesafe config
    * @return a managed resource which will return the running stream
    */
  def apply(config: FranzConfig)(persist: Array[KafkaRecord] => RIO[ZEnv, Unit]): ZManaged[ZEnv, Nothing, Fiber.Runtime[Throwable, Unit]] = {
    import config._
    batched(topic, consumerSettings, batchSize, batchWindow, deserializer, blockOnCommits)(persist)
  }

  /**
    * @param topic            the kafka topic
    * @param consumerSettings the kafka consumer settings
    * @param deserializer     the deserialization mechanism from Kafka to GenericRecords
    * @return a managed resource which will open/close the kafka stream when run
    */
  def batched(
      topic: String,
      consumerSettings: ConsumerSettings,
      batchSize: Int,
      batchLimit: scala.concurrent.duration.FiniteDuration,
      deserializer: Deserializer[GenericRecord],
      blockOnCommit: Boolean
  )(persist: Array[KafkaRecord] => RIO[ZEnv, Unit]): ZManaged[ZEnv, Nothing, Fiber.Runtime[Throwable, Unit]] = {

    def persistBatch(batch: Chunk[KafkaRecord]) = {
      val offsets = batch.map(_.offset).foldLeft(OffsetBatch.empty)(_ merge _)
      if (batch.isEmpty) {
        Task.succeed(0)
      } else {
        for {
          _ <- persist(batch.toArray)
          _ <- if (blockOnCommit) offsets.commit else offsets.commit.fork
        } yield batch.size
      }
    }

    val batchedStream = {
      batchLimit.toMillis match {
        case 0 =>
          kafkaStream(topic, deserializer).grouped(batchSize)
        case timeWindow =>
          kafkaStream(topic, deserializer).groupedWithin(batchSize, Duration.fromMillis(timeWindow))
      }
    }

    batchedStream
      .mapM(persistBatch)
      .provideCustomLayer {
        ZLayer.fromManaged(Consumer.make(consumerSettings))
      }
      .foreachManaged(_ => ZIO.unit)
      .fork
  }

  private def kafkaStream(topic: String, deserializer: Deserializer[GenericRecord]) = {
    Consumer
      .subscribeAnd(Subscription.topics(topic))
      .plainStream(Serde.string, Serde.byteArray)
      .map(KafkaRecord.decoder(topic, deserializer))
  }
}
