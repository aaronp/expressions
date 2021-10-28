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
    "be able to write to a database" ignore {
      val underTest = BatchCheck(testConfig(), zenv)
      val request = BatchCheckRequest(
        "",
        messages(),
        """batch.foreach { msg =>
          |  val value = msg.content.value
          |  for {
          |    _ <- putStr(s"publishing ${value} to ${msg.topic}")
          |  } yield ()
          |}.orDie
          |""".stripMargin
      )
    }
    "be able to run a simple script" in {
      val underTest = BatchCheck(testConfig(), zenv)
      val request = BatchCheckRequest(
        "",
        messages(),
        """batch.foreach { msg =>
          |  val value = msg.content.value
          |  for {
          |    _ <- putStr(s"publishing ${value} to ${msg.topic}")
          |  } yield ()
          |}.orDie
          |""".stripMargin
      )

      // assert results
      val response: Resp = underTest(request).value()
      response.status.code shouldBe 200
      val transformResponse = response.bodyAs[TransformResponse]
      transformResponse.messages shouldBe List("Success!")
      transformResponse.success shouldBe true
      val output: BufferConsole.Output = transformResponse.as[BufferConsole.Output].get

      output.stdOut should contain allOf ("""publishing {
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
      val conf           = testConfig()
      val underTest      = BatchCheck(conf, zenv)

      val request = BatchCheckRequest(
        "",
        messages(topic1, topic2),
        """
          |org.slf4j.LoggerFactory.getLogger("test").info(" S T A R T I N G ")
          |batch.foreach { msg =>
          |  val value = msg.content.value
          |  for {
          |    _ <- putStr(s"publishing ${value.noSpaces} to ${msg.topic}")
          |    _ = org.slf4j.LoggerFactory.getLogger("test").info(s" P U B L I S H I N G ${msg.topic}")
          |    r <- msg.key.id.asString.withValue(value).publishTo(msg.topic).either
          |    _ = org.slf4j.LoggerFactory.getLogger("test").info(" P U B L I S H E D ")
          |    _ <- putStr(s"published ${msg.key.noSpaces} to topic ${msg.topic} result : ${r.isRight}")
          |  } yield r
          |}.orDie
          |""".stripMargin
      )

      // assert results
      val response: Resp = underTest(request).value()
      response.status.code shouldBe 200
      val transformResponse = response.bodyAs[TransformResponse]
      transformResponse.messages shouldBe List("Success!")
      transformResponse.success shouldBe true
      val output: BufferConsole.Output = transformResponse.as[BufferConsole.Output].get

      val expected = List(
        s"""published {"id":"abc123"} to topic $topic1 result : true""",
        s"""publishing {"some":"content"} to $topic1""",
        s"""published {"id":"def456"} to topic $topic2 result : true""",
        s"""publishing {"some":"more content"} to $topic2"""
      )

      expected.foreach { msg =>
        output.stdOut should contain(msg)
      }

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
