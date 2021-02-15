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
    "delete topics" in {
      val all    = """""".stripMargin
      val listed = all.linesIterator.mkString(",")
      println(s"make deleteTopic topic=${listed}")
    }

    "example" in {
      import expressions._
      import expressions.template.{Context, Message}

      (context: Context[Message[DynamicJson, DynamicJson]]) =>
        val result = {
          import context._
          import expressions.client._
          import io.circe.Json
          import io.circe.syntax._

          val StoreURL = s"http://localhost:8080/rest/store"
          val url      = s"$StoreURL/${record.topic}/${record.partition}/${record.offset}"
          val body = {
            val enrichment = Json.obj(
              "timestamp" -> System.currentTimeMillis().asJson,
              "kafka-key" -> record.key.value
            )
            record.content.value.deepMerge(enrichment)
          }

          val request = HttpRequest.post(url).withBody(body.noSpaces)
          List(request)
        }

        result.foreach(println)
    }
  }
}
