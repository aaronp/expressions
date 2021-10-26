package expressions.rest.server.kafka

import expressions.rest.server.{BaseRouteTest, Disk, MappingConfig}
import zio.ZIO

class BatchSinkTest extends BaseRouteTest {
  "BatchSink" should {
    "be able to start, stop and listen to sinks" in {
      val mappingConfig = MappingConfig()
      val zioScript     = """ZIO.foreach(batch) { msg =>
                          val bar   = msg.key.foo.bar.asString
                          val value = msg.content.value
                          bar.withValue(value).publishTo(msg.topic)
                      }.unit
                      """

        def writeScriptForTopic(mappingConfig: MappingConfig, disk: Disk.Service, topic: String, script: String): ZIO[Any, Any, Unit] = {
            for {
                pathToMapping <- ZIO.fromOption(mappingConfig.lookup(topic)).catchSome {
                    case None => ZIO.fail(new Exception(s"No mapping found for topic '$topic' in $mappingConfig"))
                    }
                  _ <- disk.write(pathToMapping, script)
                } yield ()
          }

      val underTest = for {
        disk <- Disk(mappingConfig.rootConfig)
        _ <- ZIO.foreach(mappingConfig.mappings) {
          case (topic, _) =>
            writeScriptForTopic(mappingConfig, disk, topic, zioScript)
        }
        sink                    <- BatchSink.make
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
