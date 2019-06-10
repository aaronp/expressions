package pipelines.reactive

import org.scalatest.concurrent.ScalaFutures
import pipelines.socket.AddressedTextMessage
import pipelines.{BaseCoreTest, WithScheduler, WithTempDir}

class PipelineRestServiceTest extends BaseCoreTest with ScalaFutures {

  "PipelineRestService" should {
    "be able to create a transform which joins two data sources" in {

      WithScheduler { implicit sched =>
        //
        //
        //
        Given("A new push service to which we can ... push data")
        val service    = PipelineRestService(sched)
        val (first, _) = service.getOrCreatePushSource(Map("source" -> "first"))

        //
        //
        //
        When("We create a 'combineLatest' transform based on that source")
        val Left(notFoundMessage) = service.getOrCreateJoinLatestTransform("join", MetadataCriteria(Map("source" -> "notFound")))
        notFoundMessage should startWith("The source '")
        val Right((_, _)) = service.getOrCreateJoinLatestTransform("join", MetadataCriteria(Map("source" -> "first")))

        service.getOrCreateDump("before").futureValue
        service.getOrCreateDump("after").futureValue
        eventually {
          service.underlying.state().get.transformsByName.keySet shouldBe Set("dump before", "dump after", "persisted", "join")
        }

        //
        //
        //
        And("A second source is joined with a count sink using the new 'join' transform")
        val (second, _) = service.getOrCreatePushSource(Map("source" -> "second"))
        service.sinks.map(_.metadata).flatMap(_.get("name")) should contain("count")

        service.connect(MetadataCriteria(first.metadata), MetadataCriteria("name" -> "count"), Seq("dump before", "join", "dump after"))

        val pipeline = eventually {
          val Seq(found) = service.pipelines.values.toSeq
          found
        }
        pipeline.result.foreach { x =>
          println(" DONE ! " + x)
        }

        val Some(firstSrc) = service.pushSourceFor(first.id.get)
        firstSrc.push(AddressedTextMessage("first", "hello"))

        val Some(secondSrc) = service.pushSourceFor(second.id.get)
        secondSrc.push(AddressedTextMessage("second", "world"))

      }
    }

    /**
      * The scenario where we want to consume from a new source via some transformation when it is created.
      *
      * So:
      *
      * 1) we add a new 'trigger' which will connect a particular 'push' source to a sink which writes to disk,
      *    via a json serializer and then -> byte array
      *
      *
      * 2) we then add a new push source -- we should be able to see the sink connect.
      *
      * 3) then make a couple calls to push data to our new source and see the data appear on disk
      */
    "be able to create, automagically trigger a connection to, and then push to a push source" ignore {
      WithTempDir { dir =>
        WithScheduler { implicit sched =>
          Given("A new service")
          val service = PipelineRestService(sched)

          When("We register a trigger")
          val (push, _) = service.getOrCreatePushSource(Map("user" -> "foo"))
          push.metadata("user") shouldBe "foo"

          ???
        }
      }
    }
  }
}
