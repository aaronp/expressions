package pipelines.reactive

import monix.reactive.Observable
import org.scalatest.WordSpec
import pipelines.{BaseCoreTest, WithScheduler}

class PipelineServiceTest extends BaseCoreTest {

  "PipelineService" should {
    "be able to register and connect to new sources" in {
      WithScheduler { implicit scheduler =>
      val service = PipelineService()

        import implicits._


        service.getOrCreatePushSourceForUser("dave")
      }
    }
  }
}
