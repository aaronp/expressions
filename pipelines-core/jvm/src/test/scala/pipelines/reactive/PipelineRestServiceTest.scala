package pipelines.reactive

import org.scalatest.concurrent.ScalaFutures
import pipelines._
import pipelines.reactive.DataSource.PushSource
import pipelines.reactive.PipelineRestService.Settings
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}
import pipelines.rest.socket.AddressedTextMessage

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class PipelineRestServiceTest extends BaseCoreTest with ScalaFutures {

  "PipelineRestService" should {
    "be able to create a transform which joins two data sources" in {

      withScheduler { implicit sched =>
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
          service.underlying.state().get.transformsByName.keySet should contain allOf ("before", "after", "persisted", "join")
        }

        //
        //
        //
        And("A second source is joined with a count sink using the new 'join' transform")
        val (second, _) = service.getOrCreatePushSource(Map("source" -> "second"))
        service.sinks.map(_.metadata).flatMap(_.get("name")) should contain("count")

        //
        // call the 'connect' which matches the 'second' source with the 'count' sink going via the 'before', 'join', and 'after' transforms
        //
        val sourceCriteria = MetadataCriteria(second.metadata)
        val sinkCriteria   = MetadataCriteria("name" -> "count")
        service.connect(sourceCriteria, sinkCriteria, Seq("before", "join", "after"))

        val pipeline = eventually {
          val Seq(found) = service.pipelines.values.toSeq
          found
        }
        val Some(firstSrc) = service.pushSourceFor(first.id.get)
        firstSrc.push(AddressedTextMessage("first", "hello")).futureValue

        val Some(secondSrc) = service.pushSourceFor(second.id.get)
        secondSrc.push(AddressedTextMessage("second", "world")).futureValue
        firstSrc.push(AddressedTextMessage("first", "hello again")).futureValue
        secondSrc.push(AddressedTextMessage("second", "world again")).futureValue

        firstSrc.complete()
        secondSrc.complete()
        pipeline.resultFuture.futureValue shouldBe 3
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
    "be able to create, automagically trigger a connection, and then push to a push source" in {
      WithTempDir { dir =>
        withScheduler { implicit sched =>
          val settings = Settings(dir, sched)
          Given("A new service")
          val service: PipelineRestService = PipelineRestService(settings)

          When("We register a trigger which connects sources which contain a 'autoconnect' set to 'true' with a 'count' sink via the filesystem")
          eventually {
            service.transformsByName.keySet should contain("persisted")
          }

          service.getOrCreateDump("before").futureValue
          service.getOrCreateDump("after").futureValue
          service.underlying.triggers.connect(
            MetadataCriteria("autoconnect" -> "true"),
            MetadataCriteria("name"        -> "count"),
            Seq("before", "sockets.addressedTextAsJson", "Transform.jsonToString", "Transform.stringToUtf8", "persisted", "after"),
            retainTriggerAfterMatch = true,
            TriggerCallback.Ignore
          )

          And("we add a data source")
          val matches = ListBuffer[PipelineMatch]()

          val pipes = ListBuffer[Pipeline[_, _]]()
          service.underlying.pipelineCreatedEvents.foreach { next =>
            pipes += next
          }
          service.underlying.matchEvents.foreach {
            case (_, newConnection) =>
              matches += newConnection
          }

          object callback extends reactive.TriggerCallback.LoggingInstance {
            var triggeredResult = Option.empty[Either[String, Pipeline[_, _]]]
            override def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit = {
              super.onFailedMatch(input, mtch, err)
              triggeredResult = Option(Left(err))
            }

            override def onResult(response: Try[TriggerEvent]): Unit = {
              super.onResult(response)
              response match {
                case Failure(err) => triggeredResult = Some(Left(err.getMessage))
                case Success(res) =>
              }
            }

            override def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_, _]): Unit = {
              super.onMatch(input, mtch, pipeline)
              triggeredResult = Option(Right(pipeline))
            }
          }

          val (pushSource: PushSource[AddressedTextMessage], _) = service.getOrCreatePushSource(Map("user" -> "foo", "autoconnect" -> "true"), callback)

          Then("we should see the new source connect")
          val either = eventually {
            val Some(result) = callback.triggeredResult
            result
          }
          val pipeline: Pipeline[_, _] = either match {
            case Left(err) => fail(err)
            case Right(ok) => ok
          }

          eventually {
            matches.size shouldBe 1
          }

          pushSource.push(AddressedTextMessage("first", "value"))
          pushSource.push(AddressedTextMessage("second", "value"))
          pushSource.complete()

          pipeline.resultFuture.futureValue shouldBe 2
        }
      }
    }
  }
}
