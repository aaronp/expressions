package expressions.rest

import expressions.RichDynamicJson
import expressions.template.Message
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

      (context: Context[expressions.RichDynamicJson]) =>
        {
          import context._

          implicit val implicitMessageValueSoRichJsonPathAndOthersWillWork = context.record.value.jsonValue

          import expressions.client._

          import io.circe.syntax._

          val __userEndResult = {
            HttpRequest.post("unit-test").withBody(record.value.hello.world(0).name.asJsonString)
          }

          __userEndResult.asJson

        }

    }
  }
}
