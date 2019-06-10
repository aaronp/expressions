package pipelines.reactive

import pipelines.{BaseCoreTest, WithScheduler}

class PipelineServiceTest extends BaseCoreTest {

  "PipelineService.getOrCreateTransform" should {
    "create a data source for some config" in {
      WithScheduler { implicit scheduler =>
        val service = PipelineService()
//
//        service.getOrCreateTransform()

      }
    }
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
      }
    }
  }
}
