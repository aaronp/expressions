package expressions.rest.server.kafka

import expressions.DynamicJson
import expressions.rest.server.BaseRouteTest
import expressions.template.Message
import zio.{ZEnv, ZIO}

import scala.util.Success

class BatchTemplateTest extends BaseRouteTest {

  "BatchTemplate.compile" should {
    "compile" in {
      val Success(script) = BatchTemplate.compile("""ZIO.foreach(batch) { msg =>
          |    val bar   = msg.key.foo.bar.asString
          |    val value = msg.content.value
          |    bar.withValue(value).publishTo(msg.topic)
          |}.retryN(10).orDie
          |""".stripMargin)

      withClue(script.code) {
        script.inputType shouldBe "expressions.rest.server.kafka.BatchInput"
      }

      val topic            = rnd(getClass.getSimpleName)
      def j(jason: String) = DynamicJson(io.circe.parser.parse(jason).toTry.get)
      val batch = (0 to 3).map { i =>
        val key   = j(s"""{ "foo" : { "bar" : "this is key $i" } }""")
        val value = j(s"""{ "some" : { "number" : $i } }""")
        Message(value, key, topic = topic)
      }

      val underTest: ZIO[ZEnv, Throwable, Unit] = BatchContext().use { ctxt =>
        script(BatchInput(Batch(topic, batch.toVector), ctxt))
      }

      // this test (for now) just is to not throw
      underTest.value()
    }
  }
}
