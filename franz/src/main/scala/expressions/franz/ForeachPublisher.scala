package expressions.franz

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import zio.Chunk
import zio.kafka.producer.Producer

object ForeachPublisher {

  private val logger = LoggerFactory.getLogger(getClass)

  def publish[K, V](config: FranzConfig, first: ProducerRecord[K, V], theRest: ProducerRecord[K, V]*) = {
    val list: Seq[ProducerRecord[K, V]] = first +: theRest
    publishAll[K, V](config, list)
  }

  def publishAll[K, V](config: FranzConfig = FranzConfig(), records: Iterable[ProducerRecord[K, V]]) = {
    val op = for {
      k     <- config.keySerde[K]()
      v     <- config.valueSerde[V]()
      chunk = Chunk.fromIterable(records)
      p     <- config.producer.use(_.produceChunk(chunk, k, v))
    } yield p

    op.timed.map {
      case (time, result) =>
        logger.info(s"TIMING: publishAll for ${records.size} records took $time: ${records.mkString("\n\t","\n\t","\n")}")
        result
    }
  }
}
