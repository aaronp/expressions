package expressions

import example.Address
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.util
import scala.language.dynamics

class AvroExpressionsTest extends AnyWordSpec with Matchers {

  def newData(): example.Example.Builder = {
    example.Example.newBuilder().setId("foo").setSomeDouble(12.34).setSomeLong(6).setSomeText("hello").setAddresses(new util.ArrayList[Address]())
  }

  "AvroExpressions" should {
    val matching = asAvroPredicate("""value.someInt > 80 && value.someInt < 100 || (value.someInt > value.someLong)""")
    val d8a      = newData.setSomeInt(90).build
    var calls    = 0
    val results = TestThroughput(2) {
      calls = calls + 1
      matching(d8a)
    }
    s"be performant: $results" in {
      calls should be > 1
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
