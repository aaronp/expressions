package pipelines.rest.routes

import java.time.{ZoneId, ZonedDateTime}

import akka.http.scaladsl.model.headers.{HttpChallenges, _}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{complete, get, pathSingleSlash, _}
import akka.http.scaladsl.server.Route
import javax.crypto.spec.SecretKeySpec
import pipelines.users.{Claims, LoginRequest}
import pipelines.users.jwt.Hmac256
import pipelines.users.rest.UserLoginRoutes

import scala.concurrent.Future
import scala.concurrent.duration._

class AuthenticatedDirectiveTest extends BaseRoutesTest {

  case class UnderTest(override val realm: String) extends AuthenticatedDirective {

    val loginTime: ZonedDateTime = ZonedDateTime.of(2019, 1, 2, 3, 4, 5, 6, ZoneId.of("UTC"))

    var currentTime                 = loginTime
    override def now: ZonedDateTime = currentTime

    val tokenExpiry = 5.minutes
    val adminClaims = Claims.after(tokenExpiry, loginTime).forUser("admin")

    override val secret: SecretKeySpec = Hmac256.asSecret("test")

    val loginRoute = UserLoginRoutes(secret, None) {
      case LoginRequest("admin", "password") => Future.successful(Option(adminClaims))
      case LoginRequest(user, "backdoor")    => Future.successful(Option(Claims.after(5.minutes, loginTime).forUser(user)))
      case _                                 => Future.successful(None)
    }

    def route = loginRoute.routes ~ loggedInRoute

    def loggedInRoute: Route = get {
      authenticatedEither { e =>
        pathSingleSlash {
          e match {
            case Left(err) => complete(err)
            case Right(claim) =>
              complete {
                s"Logged in user is ${claim.name}"
              }
          }
        }
      }
    }
    override def loginUri(intendedPath: Uri): Uri = {
      intendedPath.copy(rawQueryString = Option(s"requestedResource=${intendedPath.path.toString()}"), fragment = None, path = Uri.Path("/login"))
    }
  }

  "AuthenticatedDirective authorize" should {
    "reply w/ an auth error for completely bogus tokens" in {
      Get("/").withHeaders(Authorization(OAuth2BearerToken("some.bad.token"))) ~> UnderTest("SomeRealm").route ~> check {
        status shouldBe StatusCodes.Unauthorized
        header("WWW-Authenticate") shouldBe Some(`WWW-Authenticate`(HttpChallenges.oAuth2("SomeRealm")))
      }
    }
    "reply w/ an auth error for tokens with invalid secrets" in {
      Given("A login route '/users/login' and a route '/' protected which requires an authenticated token")
      val underTest = UnderTest("ARealm")

      var jwtToken: String = ""

      When("A user logs in successfully")
      Post("/users/login", LoginRequest("Alice", "backdoor")) ~> underTest.route ~> check {
        status shouldBe StatusCodes.OK
        val token = header("X-Access-Token").map(_.value()).get
        jwtToken = token
      }

      jwtToken should not be (empty)

      When("the token is sent with a modified, invalid secret")
      val badSecreteToken = jwtToken + "A"
      Get("/").withHeaders(Authorization(OAuth2BearerToken(badSecreteToken))) ~> underTest.route ~> check {
        status shouldBe StatusCodes.Unauthorized
        header("X-Access-Token") shouldBe None
      }
    }
    "reply w/ an auth reject error for missing tokens" in {
      Get("/") ~> UnderTest("SomeRealm").route ~> check {
        status shouldBe StatusCodes.Unauthorized
        header("WWW-Authenticate") shouldBe Some(`WWW-Authenticate`(HttpChallenges.oAuth2("SomeRealm")))
      }
    }
    "reply w/ an auth reject error for expired tokens" in {
      Given("A login route '/users/login' and a route '/' protected which requires an authenticated token")
      val underTest = UnderTest("ARealm")

      var jwtToken: String = ""

      And("A user successfully logs in")
      Post("/users/login", LoginRequest("Alice", "backdoor")) ~> underTest.route ~> check {
        status shouldBe StatusCodes.OK
        val token = header("X-Access-Token").map(_.value()).get
        jwtToken = token
      }
      jwtToken should not be (empty)

      And("We move time on to be after the expiry time")
      underTest.currentTime = underTest.currentTime.plusMinutes(underTest.tokenExpiry.toMinutes + 1)

      Then("the user should be redirected to login again when they try to access the authenticated route")
      Get("/").withHeaders(Authorization(OAuth2BearerToken(jwtToken))) ~> underTest.route ~> check {
        status shouldBe StatusCodes.Unauthorized
        header[Location].map(_.uri.rawQueryString).foreach { x =>
          x shouldBe Option("redirectTo=/")
        }
      }
    }
    "allow routes for valid tokens and reply w/ a successful token" in {
      Given("A login route '/users/login' and a route '/' protected which requires an authenticated token")
      val underTest = UnderTest("ARealm")

      var jwtToken: String = ""

      When("A user logs in successfully")
      Post("/users/login", LoginRequest("Alice", "backdoor")) ~> underTest.route ~> check {
        status shouldBe StatusCodes.OK
        val token = header("X-Access-Token").map(_.value()).get
        jwtToken = token
      }

      jwtToken should not be (empty)

      Then("the user should be able to access the protected route")
      Get("/").withHeaders(Authorization(OAuth2BearerToken(jwtToken))) ~> underTest.route ~> check {
        status shouldBe StatusCodes.OK
        responseAs[String] shouldBe "Logged in user is Alice"
        val token = header("X-Access-Token").map(_.value()).get
        token shouldBe jwtToken
      }
    }
  }
}
