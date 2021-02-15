package expressions

import expressions.JsonTemplate.Expression
import expressions.template.Message
import io.circe.Json

import io.circe.literal._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class JsonTemplateTest extends AnyWordSpec with Matchers {
  type JsonMsg = Message[DynamicJson, DynamicJson]
  "JsonTemplate" should {
    "work" in {
      val template                                   = JsonTemplate.newCache[JsonMsg, Json]()
      val Success(script: Expression[JsonMsg, Json]) = template("""
          |        val requests = record.content.hello.world.flatMap  { json =>
          |          json.nested.map { i =>
          |            val url = s"${json("name").asString}-$i"
          |            json("name").asString match {
          |              case "first" => HttpRequest.get(url, Map.empty)
          |              case other   => HttpRequest.post(url, Map.empty)
          |            }
          |          }
          |        }
          |
          |        import io.circe.syntax._
          |        requests.asJson
          |""".stripMargin)

      val data = json"""{
        "hello" : {
          "there" : true,
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
      import DynamicJson.implicits._
      val ctxt   = Message.of(data.asDynamic).withKey(data.asDynamic).asContext()
      val result = script(ctxt).asArray.get
      result.size shouldBe 5
    }
  }
}
