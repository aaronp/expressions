package expressions.client

import io.circe.syntax._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Success

class HttpRequestTest extends AnyWordSpec with Matchers {
  "HttpRequest" should {
    "marshal to/from json" in {
      val expected = HttpRequest.post("http://foo").withBody(List(1, 2, 3).asJson.noSpaces)
      expected.asJson.as[HttpRequest].toTry shouldBe Success(expected)
    }
  }
}
