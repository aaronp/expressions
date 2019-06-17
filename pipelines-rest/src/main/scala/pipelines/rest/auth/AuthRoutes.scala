package pipelines.rest.auth

import pipelines.rest.routes.BaseCirceRoutes
import pipelines.users.{AuthEndpoints, AuthSchemas, UserAuthEndpoints}

class AuthRoutes() extends AuthEndpoints with AuthSchemas with UserAuthEndpoints with BaseCirceRoutes {

  def authRoute = {
    getUserAuth.getEndpoint.implementedBy { userId =>
      ???
    }
  }
}
