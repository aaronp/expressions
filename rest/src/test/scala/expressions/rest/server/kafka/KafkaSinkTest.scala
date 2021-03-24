package expressions.rest.server.kafka

import expressions.CodeTemplate
import expressions.client.HttpRequest
import expressions.rest.server.kafka.KafkaSink.RunningSinkId
import expressions.rest.server.{BaseRouteTest, JsonMsg}

class KafkaSinkTest extends BaseRouteTest {
  "KafkaSink" should {
    "be able to start, stop and list sinks" in {
      val compiler = CodeTemplate.newCache[JsonMsg, Seq[HttpRequest]]("import expressions.client._")
      val underTest = for {
        sink                    <- KafkaSink(compiler)
        started1: RunningSinkId <- sink.start(testConfig())
        started2: RunningSinkId <- sink.start(testConfig())
        twoRunning              <- sink.running()
        _                       = twoRunning.map(_.id) should contain only (started1, started2)
        stoppedYes              <- sink.stop(started1)
        _                       = stoppedYes shouldBe true
        stoppedNo               <- sink.stop(started1)
        _                       = stoppedNo shouldBe false
        oneRunning              <- sink.running()
        _                       = oneRunning.map(_.id) should contain only (started2)
        stoppedYes              <- sink.stop(started2)
        _                       = stoppedYes shouldBe true
        empty                   <- sink.running()
      } yield empty.map(_.id)

      underTest.value().isEmpty shouldBe true
    }
  }
}
