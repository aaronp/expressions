package pipelines.reactive

import org.scalatest.concurrent.ScalaFutures
import pipelines.reactive.DataSource.PushSource
import pipelines.reactive.PipelineRestService.Settings
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}
import pipelines.socket.AddressedTextMessage
import pipelines.{BaseCoreTest, Pipeline, WithScheduler, WithTempDir}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.util.{Success, Try}

class PipelineRestServiceTest extends BaseCoreTest with ScalaFutures {

  override def testTimeout: FiniteDuration = 13.seconds

  "PipelineRestService" should {
    "be able to create a transform which joins two data sources" ignore {

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
          service.underlying.state().get.transformsByName.keySet shouldBe Set("before", "after", "persisted", "join")
        }

        //
        //
        //
        And("A second source is joined with a count sink using the new 'join' transform")
        val (second, _) = service.getOrCreatePushSource(Map("source" -> "second"))
        service.sinks.map(_.metadata).flatMap(_.get("name")) should contain("count")

        service.connect(MetadataCriteria(second.metadata), MetadataCriteria("name" -> "count"), Seq("before", "join", "after"))

        val pipeline = eventually {
          val Seq(found) = service.pipelines.values.toSeq
          found
        }
        pipeline.result.foreach { x =>
          println(" DONE ! " + x)
        }

        val Some(firstSrc) = service.pushSourceFor(first.id.get)
        firstSrc.push(AddressedTextMessage("first", "hello")).futureValue

        val Some(secondSrc) = service.pushSourceFor(second.id.get)
        secondSrc.push(AddressedTextMessage("second", "world")).futureValue
        firstSrc.push(AddressedTextMessage("first", "hello again")).futureValue
        secondSrc.push(AddressedTextMessage("second", "world again")).futureValue

        firstSrc.complete()
        secondSrc.complete()
        pipeline.result.futureValue shouldBe 3
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
        WithScheduler { implicit sched =>
          val settings = Settings(dir, sched)
          Given("A new service")
          val service = PipelineRestService(settings)

          When("We register a trigger which connects sources which contain a 'autoconnect' set to 'true' with a 'count' sink via the filesystem")
          service.transformsByName.keySet should contain("persisted")
          service.getOrCreateDump("before")
          service.getOrCreateDump("after")
          service.underlying.triggers.connect(MetadataCriteria("autoconnect" -> "true"),
                                              MetadataCriteria("name"        -> "count"),
                                              Seq("before", "persisted", "after"),
                                              retainTriggerAfterMatch = true,
                                              Ignore)

          And("we add a data source")
          val matches = ListBuffer[PipelineMatch]()
          service.underlying.triggers.output.foreach { out =>
            println(s"\tTrigger Output: $out")
          }
          service.underlying.pipelineCreatedEvents.foreach { out =>
            println(s"""
                 |
                 |
                 |PIPELINE CREATE EVENT:
                 |$out
                 |
                 |
               """.stripMargin)
          }

          val pipes = ListBuffer[Pipeline[_]]()
          service.underlying.pipelineCreatedEvents.foreach { next =>
            println(s"""
                 |
                 | GOT $next
                 |
               """.stripMargin)
            pipes += next
          }
          service.underlying.matchEvents.foreach { newConnection: PipelineMatch =>
            println(s"new match: ${newConnection.matches.size} matches")
            matches += newConnection
          }

          var triggeredResult: Try[TriggerEvent] = null
          def callback(result: Try[TriggerEvent]) = {
            println(s"\tresult is $result")
            triggeredResult = result
          }

          val (pushSource: PushSource[AddressedTextMessage], _) = service.getOrCreatePushSource(Map("user" -> "foo", "autoconnect" -> "true"), callback)

          Then("we should see the new source connect")
          eventually {
            val Success(ok: PipelineMatch) = triggeredResult
            ok
          }

          eventually {
            matches.size shouldBe 1
          }

//          val pipeline: Pipeline[_] = eventually {
////            val Seq(r) = service.pipelines.values.toSeq
////            r
//            pipes.head._2
//          }
          while (pipes.isEmpty) {
            Thread.sleep(500)
          }

          val pipeline = pipes.head

          println("")
          pipeline.result.foreach { res =>
            println(s"Future result: " + res)
          }

          pushSource.push(AddressedTextMessage("first", "value"))
          pushSource.push(AddressedTextMessage("second", "value"))
          pushSource.complete()

          pipeline.result.futureValue shouldBe 1
        }
      }
    }
  }
}
