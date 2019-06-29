package pipelines.users.jwt

import java.time.ZonedDateTime
import java.util.Base64

import io.circe
import io.circe.Decoder.Result
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import javax.crypto.spec.SecretKeySpec

import scala.concurrent.duration.FiniteDuration

/**
  * https://en.m.wikipedia.org/wiki/JSON_Web_Token
  *
  * @param name
  * @param iss Identifies principal that issued the JWT.
  * @param sub Identifies the subject of the JWT.
  * @param aud Identifies the recipients that the JWT is intended for. Each principal intended to process the JWT must identify itself with a value in the audience claim.
  * @param exp Expires: Identifies the expiration time on and after which the JWT must not be accepted for processing
  * @param nbf NotBefore: the time on which the JWT will start to be accepted for processing
  * @param iat the time at which the JWT was issued
  * @param jti Case sensitive unique identifier of the token even among different issuers.
  */
case class Claims(
    name: String = null,
    email: String = null,
    roleStr: String = null,
    permissionsStr: String = null,
    iss: String = null,
    sub: String = null,
    aud: String = null,
    exp: Claims.NumericDate = 0L,
    nbf: Claims.NumericDate = 0L,
    iat: Claims.NumericDate = 0L,
    jti: String = null
) {
  def setRoles(first: String, theRest: String*): Claims = {
    setRoles(theRest.toSet + first)
  }

  def setRoles(roles: Set[String]): Claims = {
    roles.foreach(r => require(!r.contains(",")))
    copy(roleStr = roles.mkString(","))
  }
  def setPermissions(first: String, theRest: String*): Claims = {
    setPermissions(theRest.toSet + first)
  }

  def setPermissions(permissions: Set[String]): Claims = {
    permissions.foreach(r => require(!r.contains(",")))
    copy(permissionsStr = permissions.mkString(","))
  }

  private def asSet(str: String) = {
    Option(str).fold(Set.empty[String])(_.split(",", -1).toSet)
  }
  lazy val roles       = asSet(roleStr)
  lazy val permissions = asSet(permissionsStr)

  def isExpired(now: ZonedDateTime) = {
    exp != 0 && exp <= Claims.asNumericDate(now)
  }

  def asToken(secret: SecretKeySpec): String = JsonWebToken.asHmac256Token(this, secret)

  def asJsonWebToken(secret: SecretKeySpec): JsonWebToken = JsonWebToken.parseToken(asToken(secret)).right.get

  def toJson: String = Claims.toJson(this)

  def toJsonBase64: String = {
    val bytes = Base64.getUrlEncoder.encode(toJson.getBytes("UTF-8"))
    new String(bytes, "UTF-8")
  }
}

object Claims extends io.circe.java8.time.JavaTimeEncoders with io.circe.java8.time.JavaTimeDecoders {

  type EpochSeconds = Long

  type NumericDate = EpochSeconds

  def asNumericDate(d8: ZonedDateTime) = d8.toInstant.toEpochMilli

  implicit object ClaimsDecoder extends Decoder[Claims] {
    override def apply(c: HCursor): Result[Claims] = {
      def fld[A: Decoder](key: String, default: A) = c.downField(key).success.flatMap(_.as[A].toOption).getOrElse(default)

      Right(
        new Claims(
          name = fld[String]("name", null),
          email = fld[String]("email", null),
          roleStr = fld[String]("roles", null),
          permissionsStr = fld[String]("permissions", null),
          iss = fld[String]("iss", null),
          sub = fld[String]("sub", null),
          aud = fld[String]("aud", null),
          exp = fld[Long]("exp", 0),
          nbf = fld[Long]("nbf", 0),
          iat = fld[Long]("iat", 0),
          jti = fld[String]("jti", null)
        ))
    }
  }
  implicit object ClaimsEncoder extends Encoder[Claims] {
    override def apply(c: Claims): Json = {
      import c._

      val stringMap = Map(
        "name" -> name,
        "email" -> email,
        "roles" -> roleStr,
        "permissions" -> permissionsStr,
        "iss"  -> iss,
        "sub"  -> sub,
        "aud"  -> aud,
        "jti"  -> jti
      ).filter {
          case (_, null) => false
          case (_, "")   => false
          case _         => true
        }
        .mapValues(Json.fromString)

      val numMap = Map(
        "nbf" -> nbf,
        "iat" -> iat,
        "exp" -> exp
      ).filter {
          case (_, 0L) => false
          case _       => true
        }
        .mapValues(Json.fromLong)

      val obj = JsonObject.fromMap(stringMap ++ numMap)
      Json.fromJsonObject(obj)
    }
  }

  def after(expiry: FiniteDuration, now: ZonedDateTime = ZonedDateTime.now()) = new {

    def forUser(name: String): Claims = {
      val expires = now.plusNanos(expiry.toNanos)
      new Claims(name = name, exp = asNumericDate(expires), iat = asNumericDate(now))
    }
  }

  def toJson(c: Claims): String = {
    c.asJson.noSpaces
  }

  def fromJson(j: String): Either[circe.Error, Claims] = {
    decode[Claims](j)
  }

}
