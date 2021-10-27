package expressions.rest.server.kafka

import com.typesafe.config.ConfigFactory
import expressions.DynamicJson
import expressions.DynamicJson.*
import expressions.client.TransformResponse
import expressions.franz.FranzConfig
import expressions.rest.server.BaseRouteTest
import expressions.rest.server.RestRoutes.Resp
import expressions.template.Message
import io.circe.Json

class BatchCheckTest extends BaseRouteTest {
  "BatchCheckRequest" should {
    "be able to run a simple script" ignore {
      val underTest = BatchCheck(testConfig(), zenv)
      val request = BatchCheckRequest("", messages(), """batch.foreach { msg =>
                                                      |  val value = msg.content.value
                                                      |  for {
                                                      |    _ <- putStr(s"publishing ${value} to ${msg.topic}")
                                                      |  } yield ()
                                                      |}.orDie
                                                      |""".stripMargin)

      // assert results
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
    "be able to run a kafka publish script" in {
      val topic1: String = FranzConfig.nextRand().toLowerCase()
      val topic2: String = FranzConfig.nextRand().toLowerCase()
      val conf = testConfig()
      val underTest = BatchCheck(conf, zenv)

      val request = BatchCheckRequest("", messages(topic1, topic2), """
                                                      |println(" S T A R T I N G ")
                                                      |batch.foreach { msg =>
                                                      |  val value = msg.content.value
                                                      |  for {
                                                      |    _ <- putStr(s"publishing to ${msg.topic}")
                                                      |    _ = println(" P U B L I S H I N G ")
                                                      |    r <- msg.key.id.asString.withValue(value).publishTo(msg.topic).either
                                                      |    _ = println(" P U B L I S H E D ")
                                                      |    _ <- putStr(s"published ${msg.key} w/ result : $r")
                                                      |  } yield r
                                                      |}.orDie
                                                      |""".stripMargin)

      // assert results
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

  def messages(topic1: String = "topic-alpha", topic2: String = "topic-beta"): List[Message[DynamicJson, DynamicJson]] = s"""[
                        {
                            "content" : { "some" : "content" },
                            "key" : { "id" : "abc123" },
                            "timestamp" : 123456789,
                            "headers" : { },
                            "topic" : "$topic1",
                            "offset" : 12,
                            "partition" : 100
                        },
                        {
                            "content" : { "some" : "more content" },
                            "key" : { "id" : "def456" },
                            "timestamp" : 987654321,
                            "headers" : { "head" : "er" },
                            "topic" : "$topic2",
                            "offset" : 13,
                            "partition" : 7
                        }
                    ]""".jason.as[List[Message[DynamicJson, DynamicJson]]].toTry.get

}
