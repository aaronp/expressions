package expressions.rest.server

import expressions.client.{TransformRequest, TransformResponse}
import io.circe.Json
import io.circe.literal.JsonStringContext
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import zio.Task

import scala.util.Success

class RestRoutesTest extends BaseRouteTest {

  "POST /rest/mapping/check" should {
    "works" in {

      val underTest: HttpRoutes[Task] = RestRoutes().value()

      val req = {
        val jason = json"""{
                      "hello" : {
                        "world" : [
                          {
                            "name" : "first",
                            "nested" : [1,2,3]
                          },
                          {
                            "name" : "second",
                            "nested" : [4,5]
                          }
                        ]
                      }
                    }"""
        val script =
          """
            |
            |        record.content.hello.world.flatMap  { json =>
            |          json.nested.map { i =>
            |            val url = s"${json("name").asString}-$i"
            |            json("name").asString match {
            |              case "first" => HttpRequest.get(url, Map.empty)
            |              case other   => HttpRequest.post(url, Map.empty)
            |            }
            |          }
            |        }
            |
            |""".stripMargin
        TransformRequest(script, jason)
      }

      val Some(response)   = underTest(post("/mapping/check", req.asJson.noSpaces)).value.value()
      val Success(content) = io.circe.parser.decode[TransformResponse](response.bodyAsString).toTry
      content.messages should be(empty)
      withClue(content.result.spaces4) {
        content.result.noSpaces should not be (empty)
      }
    }
  }
}
