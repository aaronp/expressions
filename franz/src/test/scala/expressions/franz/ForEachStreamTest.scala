package expressions.franz

import io.circe.literal.JsonStringContext
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.consumer.CommittableRecord
import zio.stream.ZStream
import zio.{Chunk, Ref}

import java.nio.ByteBuffer
import java.util
import java.util.UUID

class ForEachStreamTest extends BaseFranzTest {

  def avroRecord(k: Int): GenericRecord =
    http.HttpRequest
      .newBuilder()
      .setBody(ByteBuffer.wrap(Array(1, 2, 3)))
      .setMethod(http.Method.CONNECT)
      .setUrl(s"http://example/$k")
      .setHeaders(new util.HashMap[CharSequence, CharSequence]())
      .build()

  "ForEachStream encoding" should {

    "be able to read records sent w/ different encodings" in {

      //
      // Publish "mixed" records to the same topic
      //
      val topic1 = s"foreachstreamtest1-${UUID.randomUUID()}"
      val topic2 = s"foreachstreamtest2-${UUID.randomUUID()}"
      val config = FranzConfig.stringKeyAvroValueConfig().withOverrides(s"app.franz.consumer.topic=$topic1,$topic2")
      val chunk = Chunk(
        new ProducerRecord[String, GenericRecord](topic1, "foo", avroRecord(1)),
        new ProducerRecord[String, GenericRecord](topic2, "bar", avroRecord(2))
      )

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
  "ForEachStream" should {

    "read string/avro data from a single topic" in {
      val config = FranzConfig.stringKeyAvroValueConfig()

      val chunk = Chunk.fromArray((0 to 10).map(i => new ProducerRecord(config.topic, s"key$i", avroRecord(i))).toArray)

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
    "read avro/avro data from a single topic" in {
      val config = FranzConfig.avroKeyValueConfig()

      val chunk = Chunk.fromArray((0 to 10).map { i =>
        val key: GenericRecord = SchemaGen.recordForJson(json"""{ "key" : $i, "qualifier" : "q" }""")
        new ProducerRecord[GenericRecord, GenericRecord](config.topic, key, avroRecord(i))
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
      val key: GenericRecord   = readBack.head.key
      val value: GenericRecord = readBack.head.value
      key.get("qualifier").toString shouldBe "q"
      value.get("method").toString shouldBe "CONNECT"
    }

    "read data from multiple topics" in {
      val topic1 = s"foo${UUID.randomUUID()}"
      val topic2 = s"bar${UUID.randomUUID()}"
      val config = FranzConfig.stringKeyAvroValueConfig().withOverrides(s"app.franz.consumer.topic=${topic1},${topic2}")
      val chunk = Chunk(
        new ProducerRecord[String, GenericRecord](topic1, "foo", avroRecord(1)),
        new ProducerRecord[String, GenericRecord](topic2, "bar", avroRecord(2))
      )

      val testCase = for {
        counter  <- Ref.make(List.empty[CommittableRecord[String, GenericRecord]])
        producer = config.producer[String, GenericRecord]
        _        <- producer.use(_.produceChunk(chunk))
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
