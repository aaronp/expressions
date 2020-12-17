package expressions.rest.server

import expressions.template.Message
import expressions.{JsonTemplate, RichDynamicJson}
import io.circe.Json
import io.circe.literal.JsonStringContext

import scala.util.Success

class KafkaRecordToHttpSinkTest extends BaseRouteTest {
  "KafkaRecordToHttpSink" should {
    "work" in {
      val value = System.currentTimeMillis().toInt
      val services = {
        val mappingConfig = MappingConfig()
        for {
          disk <- Disk(mappingConfig.rootConfig)
          _    <- KafkaRecordToHttpSink.writeScriptForTopic(mappingConfig, disk, "unit-test", value.toString)
          svc  <- KafkaRecordToHttpSink(mappingConfig, disk, JsonTemplate.newCache[Json]())(_.asContext())
        } yield svc
      }.value()

      val Success(thunk1) = services.mappingForTopic("unit-test")

      val ctxt = Message(new RichDynamicJson(json"""123""")).asContext()
      thunk1(ctxt) shouldBe value
    }
  }
}
