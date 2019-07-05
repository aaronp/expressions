package pipelines.reactive

import org.scalatest.concurrent.ScalaFutures
import pipelines.{BaseCoreTest, WithScheduler}

import scala.collection.mutable.ListBuffer

class PipelineServiceTest extends BaseCoreTest with ScalaFutures {

  "PipelineService.pushSourceForName" should {
    "create a new source if one doesn't already exist" in {
      WithScheduler { implicit scheduler =>
        val service           = PipelineService()
        val (true, created)   = service.pushSourceForName("test", true, false, Map("foo" -> "bar")).futureValue
        val (false, existing) = service.pushSourceForName("test", true, false, Map("foo" -> "bar")).futureValue

        created shouldBe existing
        created.metadata("foo") shouldBe "bar"
      }
    }
  }
  "PipelineService.getOrCreateSink" should {
    "link a sink which matches an existing source" in {
      WithScheduler { implicit scheduler =>
        val service = PipelineService()

        val received = ListBuffer[String]()
        val sink = DataSink.foreach[String](Map("tag" -> "test")) { x =>
          received += x
          println(x)
        }
        val Seq(createdSink) = service.getOrCreateSink(sink)
        service.sinks.size shouldBe 1
        eventually {
          service.triggers.currentState().get.sinks.size shouldBe 1
        }

        service.triggers.connect(MetadataCriteria("foo" -> "eq:bar"), MetadataCriteria("tag" -> "eq:test"), transforms = Seq("map"), retainTriggerAfterMatch = true).futureValue

        eventually {
          val st8 = service.triggers.currentState().get
          st8.sinks.size shouldBe 1
          st8.triggers.size shouldBe 1
        }

        service.triggers.addTransform("map", Transform.map((_: String).reverse)).futureValue

        eventually {
          val st8 = service.triggers.currentState().get
          st8.sinks.size shouldBe 1
          st8.triggers.size shouldBe 1
          st8.transformsByName.keySet should contain("map")
        }

        service.pipelines.size shouldBe 0

        val src: DataSource.PushSource[String] = DataSource.createPush[String].apply(scheduler).addMetadata("foo", "bar")
        val Seq(createdSource)                 = service.getOrCreateSource(src)
        createdSource.name shouldBe src.name

        eventually {
          service.pipelines.size shouldBe 1
          val (_, p) = service.pipelines.head
          p.result
        }

        src.push("value")
        src.complete()

        eventually {
          received should contain only ("value".reverse)
        }
      }
    }
  }
  "PipelineService.getOrCreateSource" should {
    "be able to register and connect to new sources" in {
      WithScheduler { implicit scheduler =>
        val service = PipelineService()

        val Seq(source)  = service.getOrCreateSource(DataSource.push[String](Map("user" -> "dave")))
        val Seq(source2) = service.getOrCreateSource(DataSource.push[String](Map("user" -> "alice")))
        source should not be (source2)
        service.sourceMetadata().map(_("user")) should contain only ("dave", "alice")

        val Seq(existing) = service.getOrCreateSource(DataSource.push[String](Map("user" -> "dave")))
        service.sourceMetadata().size shouldBe 2
        existing shouldBe source

        service.sources.list()
      }
    }
  }
}
