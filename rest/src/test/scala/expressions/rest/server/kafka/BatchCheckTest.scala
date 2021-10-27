package expressions.rest.server.kafka

import com.typesafe.config.ConfigFactory
import expressions.DynamicJson
import expressions.DynamicJson.*
import expressions.client.TransformResponse
import expressions.rest.server.BaseRouteTest
import expressions.rest.server.RestRoutes.Resp
import expressions.template.Message
import io.circe.Json

class BatchCheckTest extends BaseRouteTest {
  "BatchCheckRequest" should {
    "be able to run a working script" in {
      val underTest = BatchCheck(testConfig(), zenv)
      val request = BatchCheckRequest("", messages, script)

      val response: Resp = underTest(request).value()
      response.status.code shouldBe 200
      val transformResponse = response.bodyAs[TransformResponse]
      transformResponse.messages shouldBe List("Success!")
      transformResponse.success shouldBe true
      val output: BufferConsole.Output = transformResponse.as[BufferConsole.Output].get

      output.stdOut should contain allOf(
        """publishing {
  "some" : "content"
} to topic-alpha""",
        """publishing {
          |  "some" : "more content"
          |} to topic-beta""".stripMargin)

      output.stdErr shouldBe List()
    }
  }

  def messages: List[Message[DynamicJson, DynamicJson]] = """[
                        {
                            "content" : { "some" : "content" },
                            "key" : { "id" : "abc123" },
                            "timestamp" : 123456789,
                            "headers" : { },
                            "topic" : "topic-alpha",
                            "offset" : 12,
                            "partition" : 100
                        },
                        {
                            "content" : { "some" : "more content" },
                            "key" : { "id" : "def456" },
                            "timestamp" : 987654321,
                            "headers" : { "head" : "er" },
                            "topic" : "topic-beta",
                            "offset" : 13,
                            "partition" : 7
                        }
                    ]""".jason.as[List[Message[DynamicJson, DynamicJson]]].toTry.get


  def script =
    """batch.foreach { msg =>
      |  val value = msg.content.value
      |  for {
      |    _ <- putStr(s"publishing ${value} to ${msg.topic}")
      |  } yield ()
      |}.orDie
      |""".stripMargin

  def scriptFull =
    """val RestServer = "http://localhost:8080/rest/store/test"
      |batch.foreach { msg =>
      |  val value = msg.content.value
      |
      |  for {
      |    _ <- putStr(s"publishing to ${msg.topic}")
      |    r <- msg.key.id.asString.withValue(value).publishTo(msg.topic)
      |    url = s"$RestServer/${msg.partition}/${msg.offset}"
      |    postResponse <- post(url, msg.key.deepMerge(msg.content.value))
      |    _ <- putStr(s"published ${msg.key}")
      |    _ <- putStrErr(s"post to $url returned ${postResponse}")
      |  } yield r
      |}.orDie
      |""".stripMargin

}
