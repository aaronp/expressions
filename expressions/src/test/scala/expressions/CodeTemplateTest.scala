package expressions

import expressions.CodeTemplate.Expression
import expressions.template.Message
import io.circe.Json

import scala.util.Success

class CodeTemplateTest extends BaseTest {
  type JsonMsg = Message[DynamicJson, DynamicJson]
  "CodeTemplate" should {
    "work" in {
      val template       = CodeTemplate.newCache[JsonMsg, Json]()
      val compiledResult = template("""
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

      val script = compiledResult match {
        case Success(script: Expression[JsonMsg, Json]) => script
        case other                                      => fail(s"compiled result was: $other")
      }

      val data = """{
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
      }""".jason

      import DynamicJson.implicits._
      val ctxt   = Message.of(data.asDynamic).withKey(data.asDynamic).asContext()
      val result = script(ctxt).asArray.get
      result.size shouldBe 5
    }
  }
}
