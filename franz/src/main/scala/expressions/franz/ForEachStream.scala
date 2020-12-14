package expressions.franz

import com.typesafe.scalalogging.StrictLogging
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer
import zio._
import zio.kafka.consumer.{Consumer, ConsumerSettings, Subscription}
import zio.kafka.serde.Serde

/** A vanilla (non-batched) kafka stream which will persist each record using a provided 'saveToDatabase' function
  */
object ForEachStream extends StrictLogging {

  type JsonString = String

  /** @param config
    * @return a managed resource which will return the running stream
    */
  def apply(config: FranzConfig)(
      saveToDatabase: KafkaRecord => RIO[ZEnv, Unit]
  ): ZManaged[ZEnv, Nothing, Fiber.Runtime[Throwable, Unit]] = {
    import config._
    forEach(topic, consumerSettings, deserializer, blockOnCommits, concurrency)(saveToDatabase)
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
      topic: String,
      consumerSettings: ConsumerSettings,
      deserializer: Deserializer[GenericRecord],
      blockOnCommit: Boolean,
      concurrency: Int
  )(saveToDatabase: KafkaRecord => RIO[ZEnv, Unit]): ZManaged[ZEnv, Nothing, Fiber.Runtime[Throwable, Unit]] = {

    val plainStream = Consumer
      .subscribeAnd(Subscription.topics(topic))
      .plainStream(Serde.string, Serde.byteArray)
      .map(KafkaRecord.decoder(topic, deserializer))

    val stream = if (concurrency > 1) {
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
      .foreachManaged(_ => ZIO.unit)
      .fork
  }
}
