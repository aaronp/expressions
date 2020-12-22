package expressions

import expressions.template.Message
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StringTemplateTest extends AnyWordSpec with Matchers {

  "StringTemplate.stringAsExpression" should {
    "resolve the empty string" in {
      val expression = StringTemplate[Message[String, Int]]("")
      val ctxt       = Message.of(2).withKey("foo").asContext()
      expression(ctxt) shouldBe ""
    }
    "resolve constants" in {
      val expression = StringTemplate[Message[String, Int]]("some constant text")
      val ctxt       = Message.of(2).withKey("foo").asContext()
      expression(ctxt) shouldBe "some constant text"
    }
    "resolve a multi-part expression" in {
      val expression = StringTemplate[Message[String, Int]]("key:{{ record.key}} value: {{ record.value * 3 }}")
      val ctxt       = Message.of(2).withKey("foo").asContext()
      expression(ctxt) shouldBe "key:foo value: 6"
    }
  }
}
