package pipelines.rest.jwt
import org.scalatest.{Matchers, WordSpec}
import pipelines.users.jwt.Hmac256

class Hmac256Test extends WordSpec with Matchers {

  "Hmac256.apply" should {
    "encode a string and secret" in {

      Hmac256("secret", "content") should equal(Hmac256("secret", "content"))
      Hmac256("secret", "content") should not equal Hmac256("secret", "content2")
      Hmac256("secret", "content") should not equal Hmac256("secret2", "content")
      Hmac256("secret", "content") should not equal Hmac256("content", "secret")
    }
  }
}
