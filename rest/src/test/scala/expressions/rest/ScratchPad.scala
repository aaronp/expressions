package expressions.rest

import io.circe.literal.JsonStringContext
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
  * I just keep this around as a place to paste/debug user script code which doesn't compile
  */
class ScratchPad extends AnyWordSpec with Matchers {
  val jason =
    json"""
          {
            "in" : {
              "put" : "GET"
            }
          }
            """

  "this script" should {
    "work" in {
      import expressions._
      import expressions.implicits._
      import AvroExpressions._
      import expressions.template.{Context, Message}

      (context : Context[Message[RichDynamicJson, RichDynamicJson]]) => {
        import context._

        implicit val implicitMessageValueSoRichJsonPathAndOthersWillWork = context.record.value.jsonValue

        import expressions.client._

        import io.circe.syntax._
        import io.circe.Json

        val key = record.key.getClass
        val r = HttpRequest.post(s"http://localhost:8080/rest/store/${record.topic}/${key}/${record.partition}").withBody(record.value.jsonValue.noSpaces)
        List(r)




      }


    }
  }
}
