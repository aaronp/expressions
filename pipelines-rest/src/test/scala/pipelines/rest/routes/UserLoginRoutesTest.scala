package pipelines.rest.routes
import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.headers.{HttpChallenges, Location}
import akka.http.scaladsl.model.{HttpRequest, StatusCodes}
import akka.http.scaladsl.server.AuthenticationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection.CredentialsRejected
import pipelines.users.jwt.JsonWebToken.CorruptJwtSecret
import pipelines.users.jwt.{Hmac256, JsonWebToken}
import pipelines.users.rest.UserLoginRoutes
import pipelines.users.{Claims, LoginRequest, LoginResponse}

import scala.concurrent.Future
import scala.concurrent.duration._

class UserLoginRoutesTest extends BaseRoutesTest {

  "UserRoutes.route" should {
    // pass in a fixed 'now' time when the admin user logs in for this test
    val loginTime = ZonedDateTime.of(2019, 1, 2, 3, 4, 5, 6, ZoneId.of("UTC"))

    val adminClaims = Claims.after(5.minutes, loginTime).forUser("admin")
    val loginRoute = UserLoginRoutes("server secret") {
      case LoginRequest("admin", "password") => Future.successful(Option(adminClaims))
      case _                                 => Future.successful(None)
    }

    "reject invalid logins" in {
      Post("/users/login", LoginRequest("admin", "bad password")) ~> loginRoute.loginRoute ~> check {
        rejection shouldBe AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2(null))
      }
      Post("/users/login", LoginRequest("guest", "password")) ~> loginRoute.loginRoute ~> check {
        rejection shouldBe AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2(null))
      }
    }
    "redirect successful logins the user was redirected from another attempted page" ignore {
      val loginRequest: HttpRequest = {
        val req = Post("/users/login", LoginRequest("admin", "password"))
        req.withUri(req.uri.withRawQueryString("redirectTo=/foo/bar"))
      }
      loginRequest ~> loginRoute.loginRoute ~> check {

        header[Location].get.uri.path.toString() shouldBe "/foo/bar"
        val List(xAccessToken) = header("x-access-token").map(_.value()).toList
        xAccessToken should not be (empty)

        val Right(jwt) = JsonWebToken.forToken(xAccessToken, Hmac256.asSecret("server secret"))
        jwt.claims shouldBe adminClaims

        jwt.asToken shouldBe xAccessToken

        status shouldBe StatusCodes.TemporaryRedirect
      }
    }
    "be able to login successfully and return a jwt token" in {
      Post("/users/login", LoginRequest("admin", "password")) ~> loginRoute.loginRoute ~> check {
        val LoginResponse(true, Some(token), Some(_), None) = responseAs[LoginResponse]

        val List(xAccessToken) = header("x-access-token").map(_.value()).toList
        xAccessToken shouldBe token

        val Right(jwt) = JsonWebToken.forToken(xAccessToken, Hmac256.asSecret("server secret"))
        jwt.claims shouldBe adminClaims

        JsonWebToken.forToken(xAccessToken, Hmac256.asSecret("corrupt secret")) shouldBe Left(CorruptJwtSecret)
      }
    }
  }
}
