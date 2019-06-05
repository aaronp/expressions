package pipelines.rest.manual

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import pipelines.manual.PushEndpoints
import pipelines.rest.jwt.Claims
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}
import pipelines.socket.SocketSchemas

case class PushRoutes(secureSettings: SecureRouteSettings) extends SecureRoutes(secureSettings) with BaseCirceRoutes with PushEndpoints with SocketSchemas {

  def cancelRoute = {
    val authCancel: Directive[Tuple1[Claims]] = push.pushEndpointCancel.request.flatMap { _ =>
      authenticated
    }
    authCancel { user =>

      push.pushEndpointCancel.response()
    }
  }
  def pushPostRoute = {
    push.pushEndpointPost.implementedBy { _ =>
    }
  }
  def pushGetRoute = {
    push.pushEndpointGet.implementedBy { x =>
    }
  }
  def errorRoute = {
    push.pushEndpointError.implementedBy { msgOpt =>
      val msg = msgOpt.getOrElse("User-invoked error")

    }
  }
  def routes: Route = cancelRoute ~ pushPostRoute ~ pushGetRoute ~ errorRoute
}
