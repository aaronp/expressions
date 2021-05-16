package expressions.rest.server.kafka

import com.typesafe.config.ConfigFactory
import expressions.DynamicJson
import expressions.client.TransformResponse
import expressions.rest.server.{BaseRouteTest, Disk, LoadConfig}
import expressions.template.Message

class BatchRouteTest extends BaseRouteTest {
  "BatchCheckRequest" should {
    "be able to run a working script" in {
      val script = """batch.foreach { msg =>
                     |    val value = msg.content.value
                     |    for {
                     |      _ <- putStr(s"publishing to ${msg.topic}")
                     |      r <- msg.key.id.asString.withValue(value).publishTo(msg.topic).timeout(3.seconds)
                     |      _ <- putStr(s"published ${msg.key}")
                     |    } yield r
                     |}.orDie""".stripMargin
      import DynamicJson.implicits._
      val topicA = rnd("topic-")
      val topicB = rnd("topic-")
      val msg1 = Message[DynamicJson, DynamicJson](
        """{ "some" : "content" }""".jason.asDynamic,
        """{ "id" : "abc123" }""".jason.asDynamic,
        123456789,
        Map("head" -> "er"),
        topicA,
        12,
        100
      )
      val msg2 = Message[DynamicJson, DynamicJson](
        """{ "some" : "more content" }""".jason.asDynamic,
        """{ "id" : "def456" }""".jason.asDynamic,
        987654321,
        Map.empty,
        topicB,
        13,
        7
      )
      val request = BatchCheckRequest("", Seq(msg1, msg2), script)
      import io.circe.syntax._

      val testCase = for {
        underTest <- BatchRoute.make(LoadConfig(Disk.Service().value(), ConfigFactory.load()))
        result    <- underTest(post("batch/test", request.asJson.noSpaces)).value
      } yield result

      val Some(response) = testCase.value()
      response.status.code shouldBe 200
      val transformResponse = response.bodyAs[TransformResponse]
      transformResponse.messages shouldBe List("Success!")
      val output = transformResponse.as[BufferConsole.Output].get
      output.stdErr shouldBe (empty)
      output.stdOut should not be (empty)
      withClue(output.stdOut.mkString("\n")) {
        output.stdOut should contain only (
          s"publishing to $topicA",
          "published {\"id\":\"abc123\"}",
          s"publishing to $topicB",
          "published {\"id\":\"def456\"}",
        )
      }
    }
  }
}
