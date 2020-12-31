package expressions.rest.server

import expressions.JsonTemplate
import expressions.client.{TransformRequest, TransformResponse}
import io.circe.Json
import io.circe.literal.JsonStringContext
import io.circe.syntax._

class MappingTestRouteTest extends BaseRouteTest {

  "POST /mapping/check" should {
    "return a configuration" in {
      val script = """record.value.foo.value""".stripMargin

      val request = {
        val jason = json"""{ "foo" : "bar" }"""
        post("mapping/check", TransformRequest(script, jason).asJson.noSpaces)
      }
      val underTest = MappingTestRoute(JsonTemplate.newCache[JsonMsg, Json]().map(_.andThen(_.asJson)))

      val Some(response) = underTest(request).value.value()

      val transformResponse = response.bodyAs[TransformResponse]
      withClue(transformResponse.result.spaces2) {
        transformResponse.result.noSpaces shouldBe "\"bar\""
        response.status.isSuccess shouldBe true
      }
    }

    "return an error when misconfigured" in {
      val script = """this doesn't compile""".stripMargin

      val request = {
        val jason = json"""{ "foo" : "bar" }"""
        post("mapping/check", TransformRequest(script, jason).asJson.noSpaces)
      }
      val underTest = MappingTestRoute(JsonTemplate.newCache[JsonMsg, Json]())

      val Some(response) = underTest(request).value.value()

      val transformResponse = response.bodyAs[TransformResponse]
      withClue(transformResponse.result.spaces2) {
        val Some(err) = transformResponse.messages
        response.status.isSuccess shouldBe false
        err should include("doesn't compile")
      }
    }
  }
}
