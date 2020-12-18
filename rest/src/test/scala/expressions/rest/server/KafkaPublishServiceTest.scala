package expressions.rest.server

import expressions.client.kafka.PostRecord
import expressions.franz.{ForEachStream, FranzConfig}
import io.circe.literal.JsonStringContext
import org.apache.avro.generic.GenericRecord
import zio.Ref
import zio.kafka.consumer.CommittableRecord

class KafkaPublishServiceTest extends BaseRouteTest {

  "KafkaPublishService" should {
    "publish string records " in {
      val config   = FranzConfig.stringKeyStringValueConfig()
      val readBack = publishRecords(config)
      val key      = readBack.head.key
      val value    = readBack.head.value

      key.getClass shouldBe classOf[String]
      value.getClass shouldBe classOf[String]
    }
    "publish avro records" ignore {
      val config   = FranzConfig.avroKeyValueConfig()
      val readBack = publishRecords(config)
      val key      = readBack.head.key
      val value    = readBack.head.value

      key.getClass shouldBe classOf[GenericRecord]
      value.getClass shouldBe classOf[GenericRecord]
    }
  }

  private def publishRecords(config: FranzConfig) = {
    val recordData = json"""{ "doesnt" : "matter"  }"""
    val testRecord = PostRecord(recordData, repeat = 10)

    val testCase = for {
      posted      <- KafkaPublishService(config)(testRecord)
      _           = posted shouldBe testRecord.repeat
      readListRef <- Ref.make(List[CommittableRecord[_, _]]())
      readStream = ForEachStream[String, String](config) { record =>
        readListRef.update(record :: _).unit
      }
      job      <- readStream.runCount.fork
      readBack <- readListRef.get.repeatUntil(_.size == testRecord.repeat)
      _        <- job.interrupt
    } yield readBack

    val readBack = testCase.value()
    readBack.size shouldBe testRecord.repeat
    readBack
  }
}
