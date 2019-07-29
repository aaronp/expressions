package pipelines.reactive

import monix.reactive.Observable
import pipelines.BaseCoreTest

import scala.collection.mutable.ListBuffer

class RepoTest extends BaseCoreTest {

  "Repo.add" should {
    "add and remove sources" in {
      import implicits._
      withScheduler { s =>
        val sources = Repo.sources(s)
        val events  = ListBuffer[SourceEvent]()
        sources.events.foreach { e: SourceEvent =>
          events += e
        }(s)

        val (added, _) = sources.add(Observable(1).asDataSource())
        eventually {
          events.size shouldBe 1
        }
        val OnSourceAdded(ds, _) = events.head
        ds.metadata shouldBe added.metadata
        events.clear()
        sources.forId(added.id.get) shouldBe Some(added)

        sources.remove(added.id.get).isDefined shouldBe true

        eventually {
          events.size shouldBe 1
        }
        val OnSourceRemoved(ds2, _) = events.head
        ds2.metadata shouldBe added.metadata
        sources.forId(added.id.get) shouldBe None
      }
    }
  }
}
