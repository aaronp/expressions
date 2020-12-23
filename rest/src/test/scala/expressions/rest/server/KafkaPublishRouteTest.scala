package expressions.rest.server

import args4c.implicits._
import expressions.client.kafka.{ConsumerStats, PostRecord}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.apache.avro.generic.GenericRecord
import zio.console.putStrLn
import zio.kafka.consumer.CommittableRecord
import zio.{Ref, Task, UIO}

class KafkaPublishRouteTest extends BaseRouteTest {
  "KafkaPublishRoute" should {
    "be able to push data into a topic read by our reader route" in {

      val topic           = rnd("publishroutetest")
      val testConfig      = Array(s"app.franz.kafka.topic=$topic").asConfig()
      val expectedRecords = 10
      val testCase = for {
        //
        // setup our route under test and a test message
        //
        routeUnderTest          <- KafkaPublishRoute()
        Some(exampleRecordData) <- routeUnderTest(get("kafka/publish")).value
        recordData              = exampleRecordData.bodyAs[Json]
        testRecord              = PostRecord(recordData, topicOverride = Option(topic), repeat = expectedRecords)
        //
        // create a sink which will just keep track of our records
        //
        records <- Ref.make(List[CommittableRecord[String, GenericRecord]]())
        onRecord = (record: CommittableRecord[String, GenericRecord]) => {
          records.update(record :: _)
        }
        //
        // call our method under test - publish some records to the topic
        //
        Some(_) <- routeUnderTest(post("kafka/publish", testRecord.asJson.noSpaces)).value

        //
        // start a listener ... we should eventually read all our records
        //
        statsMap      <- Ref.make(Map[String, ConsumerStats]())
        startableSink <- KafkaSink.Service(statsMap, _ => UIO(onRecord))
        startedKey    <- startableSink.start(testConfig)
        readBack      <- records.get.repeatUntil(_.size == expectedRecords)
        stopped       <- startableSink.stop(startedKey)
        stopped2      <- startableSink.stop(startedKey)
      } yield {
        stopped shouldBe true
        stopped2 shouldBe false
        readBack
      }

      testCase.value().size shouldBe expectedRecords
    }
  }
}
