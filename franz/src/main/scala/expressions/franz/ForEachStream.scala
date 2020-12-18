package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Deserializer
import zio.stream.ZStream

/** A vanilla (non-batched) kafka stream which will persist each record using a provided 'saveToDatabase' function
  */
object ForEachStream extends StrictLogging {

  type JsonString = String

  /** @param config
    * @return a managed resource which will return the running stream
    */
  def apply[K, V](config: FranzConfig = FranzConfig())(saveToDatabase: CommittableRecord[K, V] => RIO[ZEnv, Unit]): ZStream[zio.ZEnv, Throwable, Any] = {
    import config._
    forEach(subscription, consumerSettings, keySerde[K], valueSerde[V], blockOnCommits, concurrency)(saveToDatabase)
  }

  /** This is the kafka -> DB pipeline
    *
    * It sets up a stream which writes to some sink (saveToDatabase) for each kafka record
    *
    * @param topics           the kafka topic
    * @param consumerSettings the kafka consumer settings
    * @param keyDeserializer     the deserialization mechanism from Kafka to K
    * @param valueDeserializer     the deserialization mechanism from Kafka to V
    * @return a managed resource which will open/close the kafka stream when run
    */
  def forEach[K, V](
      topics: Subscription,
      consumerSettings: ConsumerSettings,
      keyDeserializer: Deserializer[Any, K],
      valueDeserializer: Deserializer[Any, V],
      blockOnCommit: Boolean,
      concurrency: Int
  )(saveToDatabase: CommittableRecord[K, V] => RIO[ZEnv, Unit]): ZStream[zio.ZEnv, Throwable, Any] = {

    val plainStream = Consumer
      .subscribeAnd(topics)
      .plainStream(keyDeserializer, valueDeserializer)

    val stream: ZStream[zio.ZEnv with Consumer, Throwable, Offset] = if (concurrency > 1) {
      plainStream.mapMPar(concurrency) { record =>
        saveToDatabase(record).as(record.offset)
      }
    } else {
      plainStream.mapM { record =>
        saveToDatabase(record).as(record.offset)
      }
    }

    stream
      .aggregateAsync(Consumer.offsetBatches)
      .mapM(offsets => if (blockOnCommit) offsets.commit else offsets.commit.fork)
      .provideCustomLayer(ZLayer.fromManaged(Consumer.make(consumerSettings)))
  }
}
