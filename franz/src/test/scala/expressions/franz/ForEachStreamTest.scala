package expressions.franz

import io.circe.literal.JsonStringContext
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import zio.console.putStrLn
import zio.kafka.consumer.CommittableRecord
import zio.stream.ZStream
import zio.{Chunk, Ref}

import java.nio.ByteBuffer
import java.util
import java.util.UUID

class ForEachStreamTest extends BaseFranzTest {
  "ForEachStream" should {

    def record[K](topic: String, k: K): ProducerRecord[K, GenericRecord] = {
      val value = http.HttpRequest
        .newBuilder()
        .setBody(ByteBuffer.wrap(Array(1, 2, 3)))
        .setMethod(http.Method.CONNECT)
        .setUrl(s"http://example/$k")
        .setHeaders(new util.HashMap[CharSequence, CharSequence]())
        .build()
      new ProducerRecord(topic, k, value)
    }

    "read string/avro data from a single topic" in {
      val config = FranzConfig.stringKeyAvroValueConfig()

      val chunk = Chunk.fromArray((0 to 10).map(i => record(config.topic, s"key$i")).toArray)

      val testCase = for {
        counter <- Ref.make(List.empty[CommittableRecord[String, GenericRecord]])
        _       <- putStrLn("CREATING PRODUCER....")
        _       <- config.producer[String, GenericRecord].use(_.produceChunk(chunk))
        _       <- putStrLn("Closed PRODUCER....")
        stream: ZStream[zio.ZEnv, Throwable, Any] = ForEachStream[String, GenericRecord](config) { d8a =>
          counter.update(d8a +: _)
        }
        reader <- stream.take(chunk.size).runCollect.fork
        read   <- counter.get.repeatUntil(_.size == chunk.size)
        _      <- reader.interrupt
      } yield read

      val readBack: List[CommittableRecord[String, GenericRecord]] = testCase.value()
      readBack.size shouldBe chunk.size
    }
    "read avro/avro data from a single topic" in {
      val config = FranzConfig.avroKeyValueConfig()

      val chunk = Chunk.fromArray((0 to 10).map { i =>
        val key: GenericRecord = SchemaGen.recordForJson(json"""{ "key" : 1, "qualifier" : "q" }""")
        record[GenericRecord](config.topic, key)
      }.toArray)

      val testCase = for {
        counter <- Ref.make(List.empty[CommittableRecord[GenericRecord, GenericRecord]])
        _       <- config.producer[GenericRecord, GenericRecord].use(_.produceChunk(chunk))
        stream: ZStream[zio.ZEnv, Throwable, Any] = ForEachStream[GenericRecord, GenericRecord](config) { d8a =>
          counter.update(d8a +: _)
        }
        reader <- stream.take(chunk.size).runCollect.fork
        read   <- counter.get.repeatUntil(_.size == chunk.size)
        _      <- reader.interrupt
      } yield read

      val readBack: List[CommittableRecord[GenericRecord, GenericRecord]] = testCase.value()
      readBack.size shouldBe chunk.size
    }

    "read data from multiple topics" ignore {
      val topic1 = s"foo${UUID.randomUUID()}"
      val topic2 = s"bar${UUID.randomUUID()}"
      val config = FranzConfig.stringKeyAvroValueConfig().withOverrides(s"app.franz.kafka.topic=${topic1},${topic2}")
      val chunk  = Chunk(record(topic1, "foo"), record(topic2, "bar"))

      val testCase = for {
        counter <- Ref.make(List.empty[CommittableRecord[String, GenericRecord]])
        _       <- config.producer[String, GenericRecord].use(_.produceChunk(chunk))
        stream: ZStream[zio.ZEnv, Throwable, Any] = ForEachStream[String, GenericRecord](config) { d8a =>
          counter.update(d8a +: _)
        }
        reader <- stream.take(chunk.size).runCollect.fork
        read   <- counter.get.repeatUntil(_.size == chunk.size)
        _      <- reader.interrupt
      } yield read

      val readBack: List[CommittableRecord[String, GenericRecord]] = testCase.value()
      readBack.size shouldBe chunk.size
    }
  }
}
