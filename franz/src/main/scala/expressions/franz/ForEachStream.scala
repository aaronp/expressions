package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import zio._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.kafka.consumer.{Consumer, ConsumerSettings, Offset, Subscription}
import zio.kafka.serde.{Deserializer, Serde}
import zio.stream.ZStream

/** A vanilla (non-batched) kafka stream which will persist each record using a provided 'saveToDatabase' function
  */
object ForEachStream extends StrictLogging {

  type JsonString = String

  /** @param config
    * @return a managed resource which will return the running stream
    */
  def apply(config: FranzConfig = FranzConfig())(saveToDatabase: KafkaRecord => RIO[ZEnv, Unit]): ZStream[zio.ZEnv, Throwable, Any] = {
    import config._
    forEach(subscription, consumerSettings, deserializer, blockOnCommits, concurrency)(saveToDatabase)
  }

  /** This is the kafka -> DB pipeline
    *
    * It sets up a stream which writes to some sink (saveToDatabase) for each kafka record
    *
    * @param topic            the kafka topic
    * @param consumerSettings the kafka consumer settings
    * @param deserializer     the deserialization mechanism from Kafka to GenericRecords
    * @return a managed resource which will open/close the kafka stream when run
    */
  def forEach(
      topics: Subscription,
      consumerSettings: ConsumerSettings,
      deserializer: Deserializer[Any, GenericRecord],
      blockOnCommit: Boolean,
      concurrency: Int
  )(saveToDatabase: KafkaRecord => RIO[ZEnv, Unit]): ZStream[zio.ZEnv, Throwable, Any] = {

    val plainStream: ZStream[Any with Clock with Blocking with Consumer, Throwable, KafkaRecord] = Consumer
      .subscribeAnd(topics)
      .plainStream(Serde.string, Serde.byteArray)
      .mapM(KafkaRecord.decoder(deserializer))

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
