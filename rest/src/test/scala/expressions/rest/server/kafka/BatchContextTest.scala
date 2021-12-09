package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import expressions.DynamicJson
import expressions.client.HttpRequest
import expressions.rest.server.{BaseRouteTest, ConfigSummary}
import expressions.template.Message

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

      val testCase = BatchContext(testConfig).use { (ctxtUnderTest: BatchContext) =>
        val key     = DynamicJson("""{ "key" : "foo" }""".jason)
        val value   = DynamicJson("""{ "hello" : "world" }""".jason)
        val message = Message(key, value)

        import ctxtUnderTest._
        val r = message.key.withValue("""{ "msg" : "one" }""".jason).asRecord(topic)
        for {
          result1 <- message.key.withValue("""{ "msg" : "one" }""".jason).publishTo(topic)
          result2 <- message.key.withValue("""{ "msg" : "two" }""".jason).publishTo(topic)
          result3 <- message.key.withValue("""{ "msg" : "three" }""".jason).publishTo(topic2)
          result4 <- message.key.withValue("""{ "msg" : "four" }""".jason).publishTo(topic2)
        } yield List(result1, result2, result3, result4)
      }

      val results = testCase.value()

      val topicOffsets = results.map(r => r.topic() -> r.offset())
      topicOffsets should contain only ((topic, 0), (topic, 1), (topic2, 0), (topic2, 1))
    }
  }
}
