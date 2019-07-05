package pipelines.users.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}
import pipelines.users.{UserSchemas, UserService, _}

import scala.concurrent.Future

/**
  * Routes for [[UserEndpoints]], e.g. user status, create user, etc
  *
  * @param userService
  * @param secureSettings
  */
class UserRoutes(userService: UserService[Future], secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with UserEndpoints
    with UserSchemas
    with BaseCirceRoutes {

  def routes: Route = createUserRoute ~ userStatusRoute ~ resetUserRoute

  def userStatusRoute: Route = {
    val directive = userStatus.statusRequest.flatMap { _ =>
      authenticated
    }
    import pipelines.users.jwt.RichClaims._
    directive.apply { claims =>
      val jwt = claims.asToken(secureSettings.secret)
      val status = new UserStatusResponse(
        jwtToken = jwt,
        userName = claims.name,
        userId = claims.userId,
        roles = claims.roles,
        permissions = claims.permissions,
        loggedInAtUTC = claims.iat
      )
      userStatus.statusEndpoint.response(status)
    }
  }

  def createUserRoute: Route = {
    extractExecutionContext { implicit ec =>
      createUser.createUserEndpoint.implementedByAsync { req: CreateUserRequest =>
        userService.createUser(req).flatMap {
          case Left(err) =>
            Future.successful(CreateUserResponse(false, None, Some(err.description)))
          case Right(msg) =>
            import pipelines.users.jwt.RichClaims._
            // log the new user in
            userService.loginHandler.login(LoginRequest(req.userName, req.hashedPassword)).map { claimsOpt =>
              CreateUserResponse(true, claimsOpt.map(_.asToken(secureSettings.secret)), None)
            }
        }
      }
    }
  }

  /**
    * Send an email to reset a user's email
    *
    * @return
    */
  def resetUserRoute: Route = {
    extractExecutionContext { implicit ec =>
      resetUser.resetUserEndpoint.implementedByAsync { req =>
        //TODO
        sys.error(s"Not implemented for : ${req.user}")
        ???
      }
    }
  }
}

object UserRoutes {
  def apply(userService: UserService[Future], secureSettings: SecureRouteSettings): UserRoutes = {
    new UserRoutes(userService: UserService[Future], secureSettings: SecureRouteSettings)
  }
}
