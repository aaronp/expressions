package expressions.franz

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import zio.stream.ZStream
import zio.{Chunk, Ref}

import java.nio.ByteBuffer
import java.util
import java.util.UUID

class ForEachStreamTest extends BaseFranzTest {
  "ForEachStream" should {

    def record(topic: String, k: String): ProducerRecord[String, GenericRecord] = {
      val value = http.HttpRequest
        .newBuilder()
        .setBody(ByteBuffer.wrap(Array(1, 2, 3)))
        .setMethod(http.Method.CONNECT)
        .setUrl(s"http://example/$k")
        .setHeaders(new util.HashMap[CharSequence, CharSequence]())
        .build()
      new ProducerRecord(topic, k, value)
    }

    "read data from a single topic" in {
      val config = FranzConfig()

      val chunk = Chunk.fromArray((0 to 10).map(i => record(config.topic, s"key$i")).toArray)

      val testCase = for {
        counter <- Ref.make(List.empty[KafkaRecord])
        _       <- config.stringAvroProducer.use(_.produceChunk(chunk))
        stream: ZStream[zio.ZEnv, Throwable, Any] = ForEachStream(config) { d8a =>
          counter.update(d8a +: _)
        }
        reader <- stream.take(chunk.size).runCollect.fork
        read   <- counter.get.repeatUntil(_.size == chunk.size)
        _      <- reader.interrupt
      } yield read

      val readBack: List[KafkaRecord] = testCase.value()
      readBack.size shouldBe chunk.size
    }
    "read data from multiple topics" in {

      val topic1 = s"foo${UUID.randomUUID()}"
      val topic2 = s"bar${UUID.randomUUID()}"
      val config = FranzConfig(s"app.franz.kafka.topic=${topic1},${topic2}")
      val chunk  = Chunk(record(topic1, "foo"), record(topic2, "bar"))

      val testCase = for {
        counter <- Ref.make(List.empty[KafkaRecord])
        _       <- config.stringAvroProducer.use(_.produceChunk(chunk))
        stream: ZStream[zio.ZEnv, Throwable, Any] = ForEachStream(config) { d8a =>
          counter.update(d8a +: _)
        }
        reader <- stream.take(chunk.size).runCollect.fork
        read   <- counter.get.repeatUntil(_.size == chunk.size)
        _      <- reader.interrupt
      } yield read

      val readBack: List[KafkaRecord] = testCase.value()
      readBack.size shouldBe chunk.size
    }
  }
}
