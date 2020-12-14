package expressions.rest.server

import com.typesafe.config.ConfigFactory
import expressions.client.{TransformRequest, TransformResponse}
import io.circe.literal.JsonStringContext
import io.circe.syntax.EncoderOps

class ConfigTestRouteTest extends BaseRouteTest {

  "POST /mapping/check" should {
    "return a configuration" in {

      val underTest = ConfigTestRoute(_.asContext().withEnv("envi" -> "ronment"))
      val jason     = json"""{ "foo" : "bar", "values" : [0,1,2] }"""
      val script =
        """test {
           |  arr : "{{ record.value.values.each.int.toList.mkString(';'.toString) }}-{{ record.key }}-{{ env.envi }}"
           |  const : "some value"
           |}
           |x : "{{ record.value.foo.string.get }}"
          |""".stripMargin

      val Some(response) = underTest(post("config/check", TransformRequest(script, jason, "schlussel").asJson.noSpaces)).value.value()

      val transformResponse = response.bodyAs[TransformResponse]
      withClue(transformResponse.result.spaces2) {
        val cfg = ConfigFactory.parseString(transformResponse.result.spaces2)
        response.status.isSuccess shouldBe true

        cfg.getString("\"test.arr\"") shouldBe "0;1;2-schlussel-ronment"
        cfg.getString("x") shouldBe "bar"
      }
    }

    "return an error when misconfigured" in {

      val underTest = ConfigTestRoute(_.asContext().withEnv("envi" -> "ronment"))
      val jason     = json"""{ "foo" : "bar", "values" : [0,1,2] }"""
      val script =
        """broken : "{{ record.does not compile }}"
          |""".stripMargin

      val Some(response) = underTest(post("config/check", TransformRequest(script, jason, "schlussel").asJson.noSpaces)).value.value()

      val transformResponse = response.bodyAs[TransformResponse]
      withClue(transformResponse.result.spaces2) {
        val Some(msg) = transformResponse.messages
        msg should startWith("didn't work w/ input: Couldn't parse ")
        response.status.isSuccess shouldBe false
      }
    }
  }
}
