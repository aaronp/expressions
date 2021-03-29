package expressions.template

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class MessageTest extends AnyWordSpec with Matchers {
  "Message.asJson" should {
    "convert to/from json" in {
      import io.circe.syntax._
      val msg: Message[Int, String] = Message("value", 3, Long.MaxValue, Map("head" -> "er"), "topic", 123, 456)
      val jason                     = msg.asJson
      jason.as[Message[Int, String]].toTry shouldBe Success(msg)
    }
  }
}
