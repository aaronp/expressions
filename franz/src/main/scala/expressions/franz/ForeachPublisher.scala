package expressions.franz

import org.apache.kafka.clients.producer.ProducerRecord
import zio.Chunk
import zio.kafka.producer.Producer

object ForeachPublisher {

  def publish[K, V](config: FranzConfig, first: ProducerRecord[K, V], theRest: ProducerRecord[K, V]*) = {
    val list: Seq[ProducerRecord[K, V]] = first +: theRest
    publishAll[K, V](config, list)
  }

  def publishAll[K, V](config: FranzConfig = FranzConfig(), records: Iterable[ProducerRecord[K, V]]) = {
//    config.producer[K, V].use(_.produceChunk(Chunk.fromIterable(records)))
//  }
//
//  def apply[K, V](config: FranzConfig = FranzConfig(), records: Iterable[ProducerRecord[K, V]]) = {
    for {
      k     <- config.keySerde[K]()
      v     <- config.valueSerde[V]()
      chunk = Chunk.fromIterable(records)
      p     <- config.producer.use(_.produceChunk(chunk, k, v))
    } yield p
  }
}
