package pipelines.reactive.trigger

import monix.execution.Ack
import monix.reactive.subjects.Var
import monix.reactive.{Consumer, Observable}
import pipelines.reactive.DataSink.syntax._
import pipelines.reactive.DataSource.syntax._
import pipelines.reactive._
import pipelines.{BaseCoreTest, WithScheduler}

import scala.collection.mutable.ListBuffer

class TriggerPipeTest extends BaseCoreTest {

  "TriggerPipe" should {
    "match sources with sinks when a new trigger is added" in {
      WithScheduler { implicit sched =>
        val ignoredConsumer = Consumer
          .foreach[Any] { _ =>
            }
          .asDataSink("user" -> "foo")

        val (sources, sinks, pipe)                                    = TriggerPipe.create(sched)
        val ref: Var[Option[(RepoState, TriggerInput, TriggerEvent)]] = Var(Option.empty)
        pipe.output.foreach { event: (RepoState, TriggerInput, TriggerEvent) =>
          ref := Option(event)
        }

        val (s1, _) = sources.add(Observable.fromIterable(List(1, 2, 3)).asDataSource("topic" -> "test"))
        eventually {
          ref() should not be (empty)
        }
        sinks.add(ignoredConsumer)

        eventually {
          val Some((state, _, _)) = ref()
          state.sources should not be (empty)
          state.sinks should not be (empty)
        }

        pipe.connect(sourceCriteria = MetadataCriteria("topic" -> "test"), sinkCriteria = MetadataCriteria("user" -> "foo"))

        val matchEvent = eventually {
          val Some((_, _, ok: PipelineMatch)) = ref()
          ok
        }
        matchEvent.source shouldBe s1

      }

    }
    "match sources with sinks" in {
      WithScheduler { implicit sched =>
        Given("Two sources with different metadata")
        val sources: Sources      = Sources(sched)
        val (firstDataSource, _)  = sources.add(Observable.fromIterable(List(1, 2, 3)).asDataSource("topic" -> "first"))
        val (secondDataSource, _) = sources.add(Observable.fromIterable(List(4, 5, 6)).asDataSource("topic" -> "second"))

        firstDataSource.metadata("id") should not be (secondDataSource.metadata("id"))
        firstDataSource.metadata("topic") shouldBe ("first")
        secondDataSource.metadata("topic") shouldBe ("second")

        When("We connect a trigger to the sources")
        val driver    = TriggerPipe()
        val received  = ListBuffer[(RepoState, TriggerInput, TriggerEvent)]()
        val received2 = ListBuffer[(RepoState, TriggerInput, TriggerEvent)]()

        // subscribe twice, just to see/check we're not duplicating events via what would be a cold observer
        driver.output.foreach { next: (RepoState, TriggerInput, TriggerEvent) =>
          received += next
        }
        driver.output.foreach { next =>
          received2 += next
        }
        driver.subscribeToSources(sources.events)

        Then("We should observe two UnmatchedSource events")
        eventually {
          received.size shouldBe 2
          received2.size shouldBe 2
        }
        received.foreach {
          case (_, _, UnmatchedSource(_, triggers)) => triggers should be(empty)
        }
        received.clear()

        When("We add a transformation")
        val doubleT = Transform[Int, Int](_.map(_ * 2))
        driver.addTransform("double", doubleT) shouldBe Ack.Continue

        Then("We should see a transform added event")
        eventually {
          received.size shouldBe 1
          received.head._3 shouldBe TransformAdded("double")
        }
        received.clear()

        When("A trigger is added which will match a source and sink with our transform")
        val trigger = Trigger(MetadataCriteria("topic" -> "first"), MetadataCriteria("sink" -> "match me!"), Seq("double"))
        driver.connect(trigger, true, TriggerCallback.Ignore) shouldBe Ack.Continue

        Then("We should see a trigger added event")
        eventually {
          received.size shouldBe 1
        }
        received.head._3 shouldBe TriggerAdded(trigger)
        received.clear()

        //
        // Finally, let's connect some poop (I probably shouldn't swear in open source comments)!
        //
        When("We our trigger events listens to some sinks")
        val sinks = Sinks(sched)
        driver.subscribeToSinks(sinks.events)

        val unmatchedList = ListBuffer[Any]()
        val ignoredConsumer = Consumer.foreach[Any] { n =>
          unmatchedList += n
        }

        And("we add an unmatched sink")
        sinks.add(ignoredConsumer.asDataSink("sink" -> "ignore me"))

        Then("The sink should _not_ be connected")
        eventually {
          received.size shouldBe 1
        }
        locally {
          val UnmatchedSink(_, triggers) = received.head._3
          triggers should contain only (trigger)
        }
        received.clear()

        When("We add a matching sink")
        val matchedList = ListBuffer[Any]()
        val matchedConsumer = Consumer.foreach[Any] { n =>
          matchedList += n
        }

        val matchingSink = matchedConsumer.asDataSink("sink" -> "match me!")
        trigger.matchesSink(matchingSink) shouldBe true

        sinks.add(matchingSink)

        Then("The sink should match the chain")
        eventually {
          received.size shouldBe 1
        }
        withClue("the matching logic shouldn't actually connect the source and sink - just identify a match") {
          val Seq(PipelineMatch(_, src, trans, sink, _)) = received.map(_._3)
          matchedList should be(empty)
        }
        received.clear()

      }
    }
  }
}
