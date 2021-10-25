package expressions.rest.server.kafka

import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import zio.Task
import zio.blocking.Blocking
import zio.kafka.producer.Producer
import zio.kafka.serde.Serde

case class RecordBuilder[K, V](key: K, value: V, producer: Producer, blocking: Blocking, keySerde: Serde[Any, K], valueSerde: Serde[Any, V], partition: Int = -1) {
  def withPartition(p: Int): RecordBuilder[K, V] = copy(partition = p)

  def publishTo(topic: String, timestamp: Long = System.currentTimeMillis()): Task[RecordMetadata] = {
    val record = asRecord(topic, timestamp)
    producer.produce(record, keySerde, valueSerde) //.provide(blocking)
  }

  def asRecord(topic: String, timestamp: Long = System.currentTimeMillis()): ProducerRecord[K, V] = {
    if (partition <= 0) {
      new ProducerRecord[K, V](topic, key, value)
    } else {
      new ProducerRecord[K, V](topic, Integer.valueOf(partition), timestamp, key, value)
    }
  }
}
