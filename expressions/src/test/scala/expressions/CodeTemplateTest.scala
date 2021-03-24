package expressions

import expressions.CodeTemplate.Expression
import expressions.template.Message
import io.circe.Json

import io.circe.literal._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class CodeTemplateTest extends AnyWordSpec with Matchers {
  type JsonMsg = Message[DynamicJson, DynamicJson]
  "CodeTemplate" should {
    "work" in {
      val template                                   = CodeTemplate.newCache[JsonMsg, Json]()
      val Success(script: Expression[JsonMsg, Json]) = template("""
          |        import io.circe.syntax._
          |
          |        val requests = record.content.hello.world.flatMap  { json =>
          |          json.nested.map { i =>
          |            val url = s"${json("name").asString}-$i"
          |            json("name").asString match {
          |              case "first" => io.circe.Json.obj("1st" -> io.circe.Json.Null)
          |              case other   => io.circe.Json.obj("other" -> other.asJson)
          |            }
          |          }
          |        }
          |
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
