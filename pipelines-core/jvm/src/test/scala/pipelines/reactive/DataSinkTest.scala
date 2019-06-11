package pipelines.reactive

import monix.reactive.Observable
import monix.reactive.subjects.Var
import org.scalatest.concurrent.ScalaFutures
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}
import pipelines.{BaseCoreTest, WithScheduler}

import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}

class DataSinkTest extends BaseCoreTest with ScalaFutures {

  "DataSink.variable" should {
    "support updating a transform" in {
      import implicits._

      WithScheduler { implicit sched =>
        Given("An original source and a control source")
        val first   = Var[String]("first")
        val control = Var[String]("control")

        val service                  = PipelineService()
        val (sourceA: DataSource, _) = service.sources.add(first.asDataSource("user" -> "a"))
        val (controlSource, _)       = service.sources.add(control.asDataSource("type" -> "control"))

        service.triggers.output.dump("trigger").foreach {
          case (a, b, c) =>
            println(s"Trigger output: $c")
        }

        val callbackMatches = ListBuffer[PipelineMatch]()
        val matches         = ListBuffer[PipelineMatch]()
        service.matchEvents.foreach {
          case (_, pipeline) =>
            println(s"pipeline: $pipeline")
            matches += pipeline
        }

        And("A variable sink for the control and a transform which uses it")
        val someVariable          = DataSink.variable[String](Var("unset"))
        val (someVariableSink, _) = service.sinks.add(someVariable)
        val modified = Transform[String, String] { input: Observable[String] =>
          input.map { a =>
            val other = someVariable.current()
            s"source: '${a}', other: '${other}'"
          }
        }
        service.triggers.addTransform("modified", modified)

        When("We connect the control source w/ the controllable (modifiable) sink")
        MetadataCriteria(controlSource.metadata).matches(controlSource.metadata) shouldBe true
        MetadataCriteria(someVariableSink.metadata).matches(someVariableSink.metadata) shouldBe true

        eventually {
          service.state.get.sources.size shouldBe 2
          service.state.get.sinks.size shouldBe 1
          service.state.get.transformsByName.size shouldBe 1
        }

        val triggerCallback = TriggerCallback { event: Try[TriggerEvent] =>
          event match {
            case Success(value: PipelineMatch) =>
              callbackMatches += value
            case other =>
              println(other)
          }
        }

        service.triggers.connect(MetadataCriteria(controlSource.metadata), MetadataCriteria(someVariableSink.metadata), callback = triggerCallback).futureValue

        eventually {
          callbackMatches.size shouldBe 1
          service.pipelines.size shouldBe 1
        }

        And("Then connect our source via the modifiable transform")
        val received = ListBuffer[String]()
        val (sink, _) = service.sinks.add(DataSink.foreach[String]("type" -> "foreach") { msg =>
          println(msg)
          received += msg
        })

        MetadataCriteria(sourceA.metadata).matches(sourceA.metadata) shouldBe true
        MetadataCriteria(sink.metadata).matches(sink.metadata) shouldBe true
        service.triggers.connect(MetadataCriteria(sourceA.metadata), MetadataCriteria(sink.metadata), Seq("modified"), callback = triggerCallback).futureValue
        eventually {
          matches.size shouldBe 2
          service.pipelines.size shouldBe 2
        }

        eventually {
          received should contain("source: 'first', other: 'control'")
        }

        Then("We should see combined messages from our source")
        first := "first update"
        eventually {
          received should contain("source: 'first update', other: 'control'")
        }
        received.clear()
        first := "second message"
        eventually {
          received should contain only ("source: 'second message', other: 'control'")
        }
        received.clear()

        When("An update comes through on our first source")
        control := "an update"

        And("We send another message from our first source")
        first := "last message"

        Then("We should see an updated result")
        eventually {
          received should contain only ("source: 'last message', other: 'an update'")
        }
        received.clear()
      }

    }
    "support routing feeds" ignore {
      import implicits._

      WithScheduler { implicit sched =>
        Given("Three different sources")
        val first    = Var[String]("first")
        val second   = Var[String]("second")
        val third    = Var[String]("third")
        val focusVar = Var[String]("focus")

        val service                  = PipelineService()
        val (sourceA: DataSource, _) = service.sources.add(first.asDataSource("user" -> "a"))
        val (sourceB, _)             = service.sources.add(second.asDataSource("user" -> "b"))
        val (sourceC, _)             = service.sources.add(third.asDataSource("user" -> "c"))
        val (focus, _)               = service.sources.add(focusVar.asDataSource("type" -> "focus"))

        And("A transform which will choose one of them based on a name")
        val latest = DataSink.variable[(String, String)](Var("none" -> ""))

        val router = Transform[String, String] { input: Observable[String] =>
          input.map { a =>
            val (name, other) = latest.current()
            s"you:'${a}', $name '${other}'"
          }
        }
        val filter = Transform[String, String] { input: Observable[String] =>
          input.map { a =>
            val (name, other) = latest.current()
            s"you:'${a}', $name '${other}'"
          }
        }
        service.triggers.addTransform("chat", router)

        val received = ListBuffer[String]()
        val sink = DataSink.foreach[String]("type" -> "foreach") { msg =>
          println(msg)
          received += msg
        }
        service.sinks.add(sink)

        service.triggers.connect(MetadataCriteria(), MetadataCriteria("type" -> "foreach"), Seq("chat"))

      }

    }
  }
}
