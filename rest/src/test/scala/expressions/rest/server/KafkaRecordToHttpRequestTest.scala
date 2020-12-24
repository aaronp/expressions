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
      val services: KafkaRecordToHttpRequest[List[HttpRequest]] = {
        val mappingConfig = MappingConfig()
        for {
          disk          <- Disk(mappingConfig.rootConfig)
          _             <- KafkaRecordToHttpRequest.writeScriptForTopic(mappingConfig, disk, "unit-test", s"${value}")
          _             <- KafkaRecordToHttpRequest.writeScriptForTopic(mappingConfig, disk, "mapping-test", s"${value.abs}".reverse)
          templateCache = JsonTemplate.newCache[JsonMsg, List[HttpRequest]]("import expressions.client._")
          svc           <- KafkaRecordToHttpRequest.forRootConfig(mappingConfig.rootConfig, templateCache)
        } yield svc
      }.value()

      val Success(thunk1) = services.transformForTopic("unit-test")

      val ctxt = Message(new RichDynamicJson(json"""123"""), new RichDynamicJson(json"""{ "key" : "k" }""")).asContext()
      thunk1(ctxt) shouldBe value
    }
  }
}
