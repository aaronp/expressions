package expressions.rest.server

import expressions.franz.KafkaRecord
import zio.Ref

class KafkaPublishRouteTest extends BaseRouteTest {
  "KafkaPublishRoute" should {
    "be able to push data into a topic read by our reader route" in {
      val publishRoute = KafkaPublishRoute()

      val testCase = for {
        records                    <- Ref.make(List[KafkaRecord]())
        onRecord: KafkaSink.SinkIO = (record: KafkaRecord) => records.update(record :: _)
        sink                       <- KafkaSink.Service(_ => onRecord)

      } yield sink

      testCase.value()
    }
  }
}
