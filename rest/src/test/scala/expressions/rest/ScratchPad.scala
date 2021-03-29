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

    "batch" in {

      import expressions.implicits._
      import expressions.DynamicJson
      import expressions.template.Message
      import expressions.rest.server.kafka.BatchContext
      import zio._
      import zio.console._
      import io.circe.syntax._

      (_batchInput: expressions.rest.server.kafka.BatchInput) =>
        {
          import _batchInput._
          import context._
          // The mapping code transforms a context into a collection of HttpRequests
          val RestServer = "http://localhost:8080/rest/disk/store"
          batch.foreach { msg =>
            val value = msg.content.value

            for {
              _            <- putStr(s"publishing to ${msg.topic}")
              r            <- msg.key.id.asString.withValue(value).publishTo(msg.topic)
              url          = s"$RestServer/${msg.partition}/${msg.offset}"
              postResponse <- post(url, msg.key.deepMerge(msg.content.value))
              _            <- putStr(s"published ${msg.key}")
              _            <- putStrErr(s"posted ${postResponse}")
            } yield r
          }.orDie

        }

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
