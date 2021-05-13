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
      import zio.duration.durationInt
      import io.circe.syntax._

      (_batchInput: expressions.rest.server.kafka.BatchInput) => {
        import _batchInput._
        import context._
        batch.foreach { msg =>
          val value = msg.content.value
          for {
            _ <- putStr(s"publishing to ${msg.topic}")
            r <- msg.key.id.asString.withValue(value).publishTo(msg.topic).timeout(10.seconds)
            _ <- putStr(s"published ${msg.key}")
          } yield r
        }.orDie
      }

    }
  }
}
