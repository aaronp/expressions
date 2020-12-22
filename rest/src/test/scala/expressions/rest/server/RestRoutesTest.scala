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

      val underTest: HttpRoutes[Task] = RestRoutes[Json, Json]().value()

      val req = {
        val jason  = json"""{
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
        val script = """import expressions.client._
                       |
                       |record.value.hello.world.flatMapSeq { json =>
                       |json.nested.mapAs { i =>
                       |  val url = s"${json.name.string.get}-$i"
                       |  val m = json.name.get[String] match {
                       |    case "first" => HttpRequest.get(url, Map.empty)
                       |    case other   => HttpRequest.post(url, Map.empty)
                       |  }
                       |  m
                       |}
                       |}""".stripMargin
        TransformRequest(script, jason)
      }

      val Some(response)   = underTest(post("/mapping/check", req.asJson.noSpaces)).value.value()
      val Success(content) = io.circe.parser.decode[TransformResponse](response.bodyAsString).toTry
      content.messages shouldBe None
      withClue(content.result.spaces4) {
        content.result.noSpaces should not be (empty)
      }
    }
  }
}
