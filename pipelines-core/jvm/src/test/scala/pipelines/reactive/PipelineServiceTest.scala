package pipelines.reactive

import pipelines.BaseCoreTest

class PipelineServiceTest extends BaseCoreTest {

  "PipelineService" should {
    "be able to connect an AddressedMessage via the filesystem" in withScheduler { implicit sched =>
      val repo = SourceRepository()
      repo.listSources().sources shouldBe empty



      val service = PipelineService(repo)


    }
  }
}
