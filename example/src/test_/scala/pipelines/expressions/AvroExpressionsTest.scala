package pipelines

package expressions

import monix.execution.Scheduler
import monix.reactive.Consumer
import monix.reactive.subjects.ConcurrentSubject

import scala.language.dynamics

class AvroExpressionsTest extends WordSpec with Matchers {

  def newData(): Example.Builder = {
    example.Example.newBuilder().setId("foo").setSomeDouble(12.34).setSomeLong(6).setSomeText("hello")
  }

  "AvroExpressions" should {
    "be performant" in {
      val matching = asAvroPredicate("""value.someInt > 80 && value.someInt < 100 || (value.someInt > value.someLong)""")

      val testData = for {
        dbl <- Seq(10, 20, 300)
        lng <- Seq(10, 20, 300)
        id  <- Seq("a", "b", "c")
      } yield {
        example.Example.newBuilder().setId(id).setSomeDouble(dbl).setSomeLong(lng).setSomeText(s"$id $dbl $lng").build()
      }

      {
        implicit val sched = Scheduler.computation()
        try {

          val filter: ConcurrentSubject[Any, Any] = ConcurrentSubject.publishToOne[Example](sched)

          val balance = Consumer.loadBalance[Example](8, filter)

          filter.map(_ => 1).bufferTimed(1.second).map(_.size)

          matching(newData.setSomeInt(90).build) shouldBe true
        } finally {
          sched.shutdown()
        }
      }
    }
  }
  "parseRule" should {
    "be able to supply arbitrary bodies" in {

      val matching = asAvroPredicate("""value.someInt > 80 && value.someInt < 100 || (value.someInt > value.someLong)""")
      matching(newData.setSomeInt(90).build) shouldBe true
      matching(newData.setSomeInt(1000).setSomeLong(456).build) shouldBe true
      matching(newData.setSomeInt(455).setSomeLong(456).build) shouldBe false
    }
  }
  "AvroExpression" should {
    "evaluate general expressions" in {
      val thisCompilesAsAnExpression = "value.someText.asString.forall(_.isDigit)"
      val test                       = AvroExpressions.Predicate(thisCompilesAsAnExpression)
      val input                      = newData.setSomeText("123").build
      test(input) shouldBe true
    }
  }

}
