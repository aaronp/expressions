package expressions.rest.server.kafka

import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.*
import zio.blocking.Blocking
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde
import zio.duration.{given, *}
import zio.duration.Duration.{given, *}

case class RecordBuilder[K, V](key: K, value: V, producer: Producer, blocking: Blocking, keySerde: Serde[Any, K], valueSerde: Serde[Any, V], partition: Int = -1) {
  def withPartition(p: Int): RecordBuilder[K, V] = copy(partition = p)

  def publishTo(topic: String, timestamp: Long = System.currentTimeMillis()) = {
    val record = asRecord(topic, timestamp)

    def fail = {
      new Exception(s"Publish timed the fuck out trying to publish to $topic")
    }
    producer.produce(record, keySerde, valueSerde).timeout(3.seconds).flatMap {
      case Some(done) =>
        println("ok")
        ZIO.succeed(done)
      case None =>
        println("timed out")
        ZIO.fail(fail)
    }
  }

  def asRecord(topic: String, timestamp: Long = System.currentTimeMillis()): ProducerRecord[K, V] = {
    if (partition <= 0) {
      new ProducerRecord[K, V](topic, key, value)
    } else {
      new ProducerRecord[K, V](topic, Integer.valueOf(partition), timestamp, key, value)
    }
  }
}
