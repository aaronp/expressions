package pipelines.users.rest

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directives, Rejection, Route}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import javax.crypto.spec.SecretKeySpec
import pipelines.rest.routes.BaseCirceRoutes
import pipelines.users.jwt.{Hmac256, JsonWebToken}
import pipelines.users.{UserSchemas, _}

import scala.concurrent.{ExecutionContext, Future}

object UserLoginRoutes {
  def apply(secret: String, realm: Option[String] = None)(doLogin: LoginRequest => Future[Option[Claims]])(implicit ec: ExecutionContext): UserLoginRoutes = {
    apply(Hmac256.asSecret(secret), realm)(doLogin)
  }

  def apply(secret: SecretKeySpec, realm: Option[String])(doLogin: LoginRequest => Future[Option[Claims]])(implicit ec: ExecutionContext): UserLoginRoutes = {
    new UserLoginRoutes(secret, rejectionForRealm(realm), doLogin)
  }

  def rejectionForRealm(realm: Option[String]): AuthenticationFailedRejection = {
    AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, HttpChallenges.oAuth2(realm.orNull))
  }

}

/**
  * I kept this separate as it's useful to have *JUST* a login w/o it getting mixed up w/ all the other user operations (create user, reset/forget password, status, etc).
  *
  * Sometimes you JUST want a login route
  *
  * @param secret
  * @param loginRejection
  * @param doLogin
  */
class UserLoginRoutes(secret: SecretKeySpec, loginRejection: Rejection, doLogin: LoginRequest => Future[Option[Claims]])(implicit ec: ExecutionContext)
    extends LoginEndpoints
    with UserSchemas
    with BaseCirceRoutes {

  def routes: Route = loginRoute

  override def loginResponse(implicit resp: JsonResponse[LoginResponse]): LoginResponse => Route = { resp: LoginResponse =>
    resp.jwtToken match {
      case Some(token) =>
        respondWithHeader(RawHeader("X-Access-Token", token)) {
          resp.redirectTo match {
            case Some(uriString) =>
              val uri = Uri(uriString)
              logger.info(s"Login redirecting to $uri")
              Directives.redirect(uri, StatusCodes.TemporaryRedirect)
            case None =>
              Directives.complete(resp)
          }
        }
      case None =>
        logger.info(s"Login response rejecting with $loginRejection")
        Directives.reject(loginRejection)
    }
  }

  def loginAndRedirect(loginRequest: LoginRequest, redirectTo: Option[String]): Future[LoginResponse] = {
    doLogin(loginRequest).map {
      case claimsOpt @ Some(claims) =>
        logger.info(s"Successful login for ${claims.name}, redirectTo=$redirectTo")
        val token = JsonWebToken.asHmac256Token(claims, secret)
        LoginResponse(true, Option(token), claimsOpt, redirectTo)
      case None => LoginResponse(false, None, None, None)
    }
  }

  def loginRoute: Route = {

    Directives.extractRequest { rqt =>
      // An anonymous user may have tried to browse a page which requires login (e.g. a JWT token), and so upon a successful login,
      // we should redirect to the URL as specified by the 'redirectToHeader' (if set)
      userLogin.redirectHeader { redirectToHeader: Option[String] =>
        userLogin.loginEndpoint.implementedByAsync {
          case (loginRequest, redirectToIn: Option[String]) =>
            val redirectTo: Option[String] = redirectToIn.orElse(redirectToHeader).orElse {
              rqt.uri.queryString().flatMap { rawQueryStr =>
                Query(rawQueryStr).get("redirectTo")
              }
            }
            loginAndRedirect(loginRequest, redirectToIn)
        }
      }
    }
  }
}
