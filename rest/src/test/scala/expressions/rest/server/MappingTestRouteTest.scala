package expressions.rest.server

import expressions.CodeTemplate
import expressions.client.{TransformRequest, TransformResponse}
import io.circe.Json
import io.circe.syntax._

class MappingTestRouteTest extends BaseRouteTest {

  "POST /mapping/check" should {
    "return a configuration" in {
      val script = """record.content.foo.value""".stripMargin

      val request = {
        val jason = """{ "foo" : "bar" }""".jason
        post("mapping/check", TransformRequest(script, jason).asJson.noSpaces)
      }
      val underTest = MappingTestRoute(CodeTemplate.newCache[JsonMsg, Json]().map(_.andThen(_.asJson)))

      val Some(response) = underTest(request).value.value()

      val transformResponse = response.bodyAs[TransformResponse]
      withClue(transformResponse.result.spaces2) {
        transformResponse.result.noSpaces shouldBe "\"bar\""
        response.status.isSuccess shouldBe true
      }
    }

    "return an error when misconfigured" in {
      val script = "this doesn't compile"

      val request = {
        val jason = """{ "foo" : "bar" }""".jason
        post("mapping/check", TransformRequest(script, jason).asJson.noSpaces)
      }
      val underTest = MappingTestRoute(CodeTemplate.newCache[JsonMsg, Json]())

      val Some(response) = underTest(request).value.value()

      val transformResponse = response.bodyAs[TransformResponse]
      withClue(transformResponse.result.spaces2) {
        transformResponse.success shouldBe false
        val List(err) = transformResponse.messages
        err should include(script)
        response.status.isSuccess shouldBe true
      }
    }
  }
}
