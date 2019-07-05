package pipelines.auth.rest

import pipelines.auth.{AuthEndpoints, AuthSchemas}
import pipelines.rest.routes.BaseCirceRoutes

class AuthRoutes() extends AuthEndpoints with AuthSchemas with BaseCirceRoutes {

  def routes = Nil

}
