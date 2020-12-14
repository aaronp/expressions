package expressions.rest.server

import expressions.client.{TransformRequest, TransformResponse}
import io.circe.literal.JsonStringContext
import io.circe.syntax._

class MappingTestRouteTest extends BaseRouteTest {

  "POST /mapping/check" should {
    "return a configuration" in {

      val script = """record.value.foo.get[String]""".stripMargin

      val request = {
        val jason = json"""{ "foo" : "bar" }"""
        post("mapping/check", TransformRequest(script, jason).asJson.noSpaces)
      }
      val underTest = MappingTestRoute()

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
      val underTest = MappingTestRoute()

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
