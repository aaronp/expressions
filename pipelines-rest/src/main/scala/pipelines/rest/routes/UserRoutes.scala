package pipelines.rest.routes

import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.model.headers.{HttpChallenges, RawHeader}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{AuthenticationFailedRejection, Directives, Rejection, Route}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport._
import javax.crypto.spec.SecretKeySpec
import pipelines.admin.UserSchemas
import pipelines.rest.jwt.{Claims, Hmac256, JsonWebToken}
import pipelines.users._

import scala.concurrent.{Await, ExecutionContext, Future}

object UserRoutes {

  def apply(secret: String, realm: Option[String] = None)(doLogin: LoginRequest => Future[Option[Claims]])(implicit ec: ExecutionContext): UserRoutes = {
    apply(Hmac256.asSecret(secret), realm)(doLogin)
  }

  def apply(secret: SecretKeySpec, realm: Option[String])(doLogin: LoginRequest => Future[Option[Claims]])(implicit ec: ExecutionContext): UserRoutes = {
    new UserRoutes(secret, rejectionForRealm(realm), doLogin)
  }

  def rejectionForRealm(realm: Option[String]): AuthenticationFailedRejection = {
    AuthenticationFailedRejection(AuthenticationFailedRejection.CredentialsRejected, HttpChallenges.oAuth2(realm.orNull))
  }
}

class UserRoutes(secret: SecretKeySpec, loginRejection: Rejection, doLogin: LoginRequest => Future[Option[Claims]])(implicit ec: ExecutionContext)
    extends UserEndpoints
    with UserSchemas
    with BaseCirceRoutes {

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

  def loginRoute: Route = {

    Directives.extractRequest { rqt =>
      // An anonymous user may have tried to browse a page which requires login (e.g. a JWT token), and so upon a successful login,
      // we should redirect to the URL as specified by the 'redirectToHeader' (if set)
      redirectHeader { redirectToHeader: Option[String] =>
        loginEndpoint.implementedByAsync {
          case (loginRequest, redirectToIn) =>
            doLogin(loginRequest).map {
              case Some(claims) =>
                val redirectTo: Option[String] = redirectToIn.orElse(redirectToHeader).orElse {
                  rqt.uri.queryString().flatMap { rawQueryStr =>
                    Query(rawQueryStr).get("redirectTo")
                  }
                }
                logger.info(s"Successful login for ${claims.name}, redirectTo=$redirectTo")
                val token = JsonWebToken.asHmac256Token(claims, secret)
                LoginResponse(true, Option(token), redirectTo)

              case None =>
                LoginResponse(false, None, None)
            }
        }
      }
    }
  }

  def createUserRoute: Route = {

    implicit def createUserRequestSchema: JsonSchema[CreateUserRequest]   = JsonSchema(implicitly, implicitly)
    implicit def createUserResponseSchema: JsonSchema[CreateUserResponse] = JsonSchema(implicitly, implicitly)

    createUserEndpoint.implementedBy { req =>
      ???
    }
  }

  def routes: Route = loginRoute ~ createUserRoute
}
