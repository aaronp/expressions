package expressions.rest

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/**
  * I just keep this around as a place to paste/debug user script code which doesn't compile
  */
class ScratchPad extends BaseRestTest {
  val testData = """
          {
            "in" : {
              "put" : "GET"
            }
          }
            """.jason

  "this script" should {
    "delete topics" ignore {
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
      import zio.duration.durationInt
      import scalikejdbc._

      (_batchInput: expressions.rest.server.kafka.BatchInput) => {
        import _batchInput.{given, *}
        import context._
        // The mapping code transforms a context into a collection of HttpRequests
        val RestServer = "http://localhost:8080/rest/store/test"
        batch.foreach { msg =>
          val value = msg.content.value

          for {
            _ <- putStr(s"publishing to ${msg.topic}")
            _ = org.slf4j.LoggerFactory.getLogger("test").info(s" P U B L I S H I N G ${msg.topic}")
            // justinFiber <- msg.key.id.asString.withValue(value).publishTo(msg.topic).fork
            updates <- sql"insert into Materialised (kind, id, version, record) values (${msg.topic}, ${msg.key.asString}, ${msg.offset}, ${value.noSpaces})".run
            url = s"$RestServer/${msg.partition}/${msg.offset}"
            postResponse <- post(url, msg.key.deepMerge(msg.content.value))
            _ <- putStr(s"published ${msg.key}")
            _ <- putStrErr(s"post to $url returned ${postResponse}")
          } yield ()
        }.orDie

      }

    }
  }
}
