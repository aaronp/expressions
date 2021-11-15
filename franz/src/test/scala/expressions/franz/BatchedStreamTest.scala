package expressions.franz

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.ProducerRecord
import zio.kafka.consumer.CommittableRecord
import zio.stream.ZStream
import zio.{Chunk, Ref}

import java.nio.ByteBuffer
import java.util
import java.util.UUID

class BatchedStreamTest extends BaseFranzTest {

  "BatchedStream" should {

    "read back chunks of data" in {
      val topic1 = s"foo${UUID.randomUUID()}"
      val config = FranzConfig.stringKeyAvroValueConfig().withOverrides(s"app.franz.consumer.topic=${topic1}")
      val chunk = Chunk.fromIterable((0 to 100).map { i =>
        new ProducerRecord[String, GenericRecord](topic1, s"foo$i", avroRecord(i))
      })

      val testCase = for {
        counter  <- Ref.make(List.empty[Array[CommittableRecord[_, _]]])
        producer = config.producer
        k        <- config.keySerde()
        v        <- config.valueSerde()
        _        <- producer.use(_.produceChunk(chunk, k, v))
        batched  <- BatchedStream[String, GenericRecord](config)
        stream: ZStream[zio.ZEnv, Throwable, Any] = batched.copy(batchSize = 3).run { (d8a: Array[CommittableRecord[_, _]]) =>
          counter.update(d8a +: _)
        }
        reader <- stream.take(chunk.size).runCollect.fork
        read   <- counter.get.repeatUntil(_.map(_.length).sum == chunk.size)
        _      <- reader.interrupt
      } yield read

      val readBackChunks = testCase.value()
      readBackChunks.size should be > 1
      readBackChunks.size should be < chunk.size
      withClue(s"Batch sizes: ${readBackChunks.map(_.length)}") {
        readBackChunks.forall(batch => batch.length > 1 && batch.length <= 3) shouldBe true
      }
    }
  }

  def avroRecord(k: Int): GenericRecord =
    http.HttpRequest
      .newBuilder()
      .setBody(ByteBuffer.wrap(Array(1, 2, 3)))
      .setMethod(http.Method.CONNECT)
      .setUrl(s"http://example/$k")
      .setHeaders(new util.HashMap[CharSequence, CharSequence]())
      .build()
}
