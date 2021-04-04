package expressions.rest.server.kafka

import expressions.client.HttpRequest
import expressions.rest.server.{BaseRouteTest, Disk, JsonMsg, MappingConfig}
import expressions.template.Message
import expressions.{CodeTemplate, DynamicJson}
import io.circe.literal.JsonStringContext

import scala.util.Success

class KafkaRecordToHttpRequestTest extends BaseRouteTest {
  "KafkaRecordToHttpRequest.transformForTopic" should {
    "run the script against some json input" in {
      val value = System.currentTimeMillis().toInt
      val services: KafkaRecordToHttpRequest[Seq[HttpRequest]] = {
        val mappingConfig = MappingConfig()
        for {
          disk          <- Disk(mappingConfig.rootConfig)
          _             <- KafkaRecordToHttpRequest.writeScriptForTopic(mappingConfig, disk, "unit-test", s"${value}")
          _             <- KafkaRecordToHttpRequest.writeScriptForTopic(mappingConfig, disk, "mapping-test", s"${value.abs}".reverse)
          templateCache = CodeTemplate.newCache[JsonMsg, Seq[HttpRequest]]("import expressions.client._")
          svc           <- KafkaRecordToHttpRequest.forRootConfig(mappingConfig.rootConfig, templateCache)
        } yield svc
      }.value()

      val Success(thunk1) = services.transformForTopic("unit-test")

      val ctxt = Message(DynamicJson(json"""123"""), DynamicJson(json"""{ "key" : "k" }""")).asContext()
      thunk1(ctxt) shouldBe value
    }
  }
}
