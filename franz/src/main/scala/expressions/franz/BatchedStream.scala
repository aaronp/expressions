package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.duration.Duration
import zio.kafka.consumer.{CommittableRecord, Consumer, ConsumerSettings, OffsetBatch, Subscription}
import zio.kafka.serde.{Deserializer, Serde}
import zio.stream.ZStream

/** A Kafka stream which will batch up records by the least of either a time-window or max-size,
  * and then use the provided 'persist' function on each batch
  */
object BatchedStream extends StrictLogging {

  type JsonString = String

  /** @param config our parsed typesafe config
    * @return a managed resource which will return the running stream
    */
  def apply[K,V](config: FranzConfig = FranzConfig())(persist: Array[CommittableRecord[K, V]] => RIO[ZEnv, Unit]) = {
    import config._
    batched(subscription, consumerSettings, batchSize, batchWindow, keySerde[K], valueSerde[V], blockOnCommits)(persist)
  }

  /**
    * @param topic            the kafka topic
    * @param consumerSettings the kafka consumer settings
    * @param deserializer     the deserialization mechanism from Kafka to GenericRecords
    * @return a managed resource which will open/close the kafka stream when run
    */
  def batched[K,V](
      topic: Subscription,
      consumerSettings: ConsumerSettings,
      batchSize: Int,
      batchLimit: scala.concurrent.duration.FiniteDuration,
      keyDeserializer: Deserializer[Any, K],
      valueDeserializer: Deserializer[Any, V],
      blockOnCommit: Boolean
  )(persist: Array[CommittableRecord[K, V]] => RIO[ZEnv, Unit]) = {

    def persistBatch(batch: Chunk[CommittableRecord[K, V]]): ZIO[zio.ZEnv, Throwable, Int] = {
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
          kafkaStream(topic, keyDeserializer, valueDeserializer).grouped(batchSize)
        case timeWindow =>
          kafkaStream(topic, keyDeserializer, valueDeserializer).groupedWithin(batchSize, Duration.fromMillis(timeWindow))
      }
    }

    batchedStream
      .mapM(persistBatch)
      .provideCustomLayer {
        ZLayer.fromManaged(Consumer.make(consumerSettings))
      }
  }

  private def kafkaStream[K,V](topic: Subscription, keyDeserializer: Deserializer[Any, K], valueDeserializer: Deserializer[Any, V]): ZStream[Clock with Blocking with Consumer, Throwable, CommittableRecord[K, V]] = {
    Consumer
      .subscribeAnd(topic)
      .plainStream(keyDeserializer, valueDeserializer)
//      .mapM(KafkaRecord.decoder(deserializer))
  }
}
