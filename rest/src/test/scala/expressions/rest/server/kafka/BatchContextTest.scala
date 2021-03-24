package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import expressions.DynamicJson
import expressions.client.HttpRequest
import expressions.rest.server.{BaseRouteTest, ConfigSummary}
import expressions.template.Message
import io.circe.literal.JsonStringContext

class BatchContextTest extends BaseRouteTest {
  "BatchedContext" should {
    "support publishing for avro/string types" in {
      val (topic, topic2) = {
        val now      = System.currentTimeMillis()
        val testName = getClass.getSimpleName.filter(_.isLetterOrDigit)
        (s"test-${testName}${now}", s"test-${testName}${now + 1}")
      }

      val testConfig: Config = {
        import args4c.implicits._
        val summary = ConfigSummary(topic, List("localhost:9092"), Map.empty, "avro:testkey", "string", "string", "string")
        ConfigFactory.load(summary.asConfig())
      }

      val testCase = BatchContext(testConfig).use { ctxtUnderTest: BatchContext =>
        val key     = DynamicJson(json"""{ "key" : "foo" }""")
        val value   = DynamicJson(json"""{ "hello" : "world" }""")
        val message = Message(key, value)

        import ctxtUnderTest._
        for {
          google  <- send(HttpRequest.get("http://www.google.com"))
          result1 <- message.key.withValue(json"""{ "msg" : "one" }""").publishTo(topic)
          result2 <- message.key.withValue(json"""{ "msg" : "two" }""").publishTo(topic)
          result3 <- message.key.withValue(json"""{ "msg" : "three" }""").publishTo(topic2)
          result4 <- message.key.withValue(json"""{ "msg" : "four" }""").publishTo(topic2)
        } yield (google, List(result1, result2, result3, result4))
      }

      val (google, results) = testCase.value()

      google.statusCode shouldBe 200
      google.body should not be (empty)
      val topicOffsets = results.map(r => r.topic() -> r.offset())
      topicOffsets should contain only ((topic, 0), (topic, 1), (topic2, 0), (topic2, 1))
    }
  }
}
