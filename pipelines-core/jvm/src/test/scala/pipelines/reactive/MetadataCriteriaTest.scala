package pipelines.reactive

import monix.reactive.Consumer
import pipelines.BaseCoreTest

class MetadataCriteriaTest extends BaseCoreTest {

  "MetadataCriteria.regex" should {
    "match data sinks" in {
      import DataSink.syntax._

      val criteria = MetadataCriteria("sink" -> "match me!")
      val matchingSink = Consumer
        .foreach[Any] { _ =>
          }
        .asDataSink("sink" -> "match me!")
      criteria.matches(matchingSink.metadata) shouldBe true
    }
    "match regular expressions" in {
      MetadataCriteria.regex("foo/.*".r).apply("foo/one") shouldBe true
      MetadataCriteria.regex("foo/.*".r).apply("foo/") shouldBe true
      MetadataCriteria.regex("foo/.*".r).apply("bar/") shouldBe false

      MetadataCriteria("topic" -> "re:foo/.*").matches("topic"          -> "foo/bar") shouldBe true
      MetadataCriteria("topic" -> "re:foo/.*").matches("something else" -> "foo/bar") shouldBe false
    }
  }
  "MetadataCriteria.any" should {
    "match when the value contains any of the strings" in {
      val criteria = MetadataCriteria("text" -> "any:one, 2, three")
      criteria.matches("text" -> "one") shouldBe true
      criteria.matches("text" -> "2") shouldBe true
      criteria.matches("text" -> "three") shouldBe true
      criteria.matches("text" -> "four") shouldBe false
      criteria.matches("text" -> "") shouldBe false
    }
  }
  "MetadataCriteria.all" should {
    "match when the value contains all of the strings" in {
      val criteria = MetadataCriteria("text" -> "all:one, 2, three")
      criteria.matches("text" -> "one") shouldBe false
      criteria.matches("text" -> "2") shouldBe false
      criteria.matches("text" -> "5, four, three, 2, one") shouldBe true
      criteria.matches("text" -> "three, 2, one") shouldBe true
    }
  }
  "MetadataCriteria.eq" should {
    "match exact text" in {
      val criteria = MetadataCriteria("text" -> "eq:ExactLY")
      criteria.matches("text" -> "ExactLY") shouldBe true
      criteria.matches("text" -> "exactly") shouldBe false
      criteria.matches("text" -> "meh") shouldBe false
    }
  }
  "MetadataCriteria with no prefix" should {
    "match exact text" in {
      val criteria = MetadataCriteria("text" -> "ExactLY")
      criteria.matches("text" -> "ExactLY") shouldBe true
      criteria.matches("text" -> "exactly") shouldBe false
      criteria.matches("text" -> "meh") shouldBe false
    }
  }
  "MetadataCriteria case insensitive" should {
    "match case insensitive text" in {
      val criteria = MetadataCriteria("text" -> "ci:ExactLY")
      criteria.matches("text" -> "ExactLY") shouldBe true
      criteria.matches("text" -> "exactly") shouldBe true
      criteria.matches("text" -> "meh") shouldBe false
    }
  }
  "MetadataCriteria" should {
    "only match when all criteria is satisfied" in {

      val criteria = MetadataCriteria("topic" -> "re:foo/.*", "role" -> "any:admin, dave")
      criteria.matches("topic" -> "foo/bar", "role"      -> "dave") shouldBe true
      criteria.matches("topic" -> "foo/bar", "role"      -> "admin") shouldBe true
      criteria.matches("topic" -> "foo/", "role"         -> "admin") shouldBe true
      criteria.matches("topic" -> "foo/bar/bazz", "role" -> "admin") shouldBe true

      criteria.matches("topic" -> "foo/bar", "role" -> "user") shouldBe false
      criteria.matches("topic" -> "bar/foo", "role" -> "user") shouldBe false
      criteria.matches("topic" -> "bar/foo", "role" -> "admin") shouldBe false
      criteria.matches("role"  -> "admin") shouldBe false
      criteria.matches("topic" -> "foo/bar") shouldBe false

    }
  }
}
