package pipelines.reactive

import monix.reactive.Observable
import pipelines.{BaseCoreTest, WithScheduler}

import scala.collection.mutable.ListBuffer

class RepoTest extends BaseCoreTest {

  "Repo.add" should {
    "add and remove sources" in {
      import implicits._
      WithScheduler { s =>
        val sources = Repo.sources(s)
        val events  = ListBuffer[SourceEvent]()
        sources.events.foreach { e: SourceEvent =>
          events += e
        }(s)

        val (added, _) = sources.add(Observable(1).asDataSource())
        eventually {
          events should contain only (OnSourceAdded(added, Ignore))
        }
        events.clear()
        sources.get(added.metadata("id")) shouldBe Some(added)

        sources.remove(added.metadata("id")).isDefined shouldBe true

        eventually {
          events should contain only (OnSourceRemoved(added, Ignore))
        }
        sources.get(added.metadata("id")) shouldBe None
      }
    }
  }
}
