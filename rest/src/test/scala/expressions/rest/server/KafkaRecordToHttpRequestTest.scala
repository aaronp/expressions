package expressions.rest.server

import expressions.client.HttpRequest
import expressions.template.Message
import expressions.{JsonTemplate, RichDynamicJson}
import io.circe.Json
import io.circe.literal.JsonStringContext

import scala.util.Success

class KafkaRecordToHttpRequestTest extends BaseRouteTest {
  "KafkaRecordToHttpRequest" should {
    "work" in {
      val value = System.currentTimeMillis().toInt
      val services = {
        val mappingConfig = MappingConfig()
        for {
          disk <- Disk(mappingConfig.rootConfig)
          _    <- KafkaRecordToHttpRequest.writeScriptForTopic(mappingConfig, disk, "unit-test", value.toString)
          svc  <- KafkaRecordToHttpRequest[Json, Json](mappingConfig, disk, JsonTemplate.newCache[JsonMsg, HttpRequest]())(_.asContext())
        } yield svc
      }.value()

      val Success(thunk1) = services.mappingForTopic("unit-test")

      val ctxt = Message(new RichDynamicJson(json"""123"""), new RichDynamicJson(json"""{ "key" : "k" }""")).asContext()
      thunk1(ctxt) shouldBe value
    }
  }
}
