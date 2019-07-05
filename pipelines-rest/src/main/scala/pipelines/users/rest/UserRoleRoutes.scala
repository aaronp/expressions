package pipelines.users.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pipelines.audit.AuditSchemas
import pipelines.auth.SetRolesForUserRequest
import pipelines.core.GenericMessageResult
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}
import pipelines.users.{UserRoleEndpoints, UserSchemas, UserService}

import scala.concurrent.Future

object UserRoleRoutes {
  def apply(userService: UserService[Future], secureSettings: SecureRouteSettings) = {
    new UserRoleRoutes(userService, secureSettings)
  }
}

class UserRoleRoutes(userService: UserService[Future], secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with UserRoleEndpoints
    with UserSchemas
    with AuditSchemas
    with BaseCirceRoutes {

  def routes: Route = {
    listUserRolesRoute ~ updateUserRolesRoute
  }

  def listUserRolesRoute: Route = {
    listUserRoles.getEndpoint.implementedByAsync { _ =>
      userService.userRolesOpt()
    }
  }

  def updateUserRolesRoute: Route = {
    extractExecutionContext { implicit ec =>
      authenticated { claims =>
        updateUserRoles.postEndpoint.implementedByAsync { request: SetRolesForUserRequest =>
          val result = userService.updateUserRoles(claims.userId, request)
          result.map {
            case Right(msg: GenericMessageResult) => msg
            case Left(err)                        => throw err
          }
        }
      }
    }
  }
}
