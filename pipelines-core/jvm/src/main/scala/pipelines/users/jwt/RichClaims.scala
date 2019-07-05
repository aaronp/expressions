package pipelines.users.jwt

import javax.crypto.spec.SecretKeySpec
import pipelines.users.Claims

class RichClaims(val claims: Claims) extends AnyVal {

  def asToken(secret: SecretKeySpec): String = JsonWebToken.asHmac256Token(claims, secret)

  def asJsonWebToken(secret: SecretKeySpec): JsonWebToken = JsonWebToken.parseToken(asToken(secret)).right.get

}

object RichClaims {
  implicit def asRichClaims(claims: Claims) = new RichClaims(claims)
}
