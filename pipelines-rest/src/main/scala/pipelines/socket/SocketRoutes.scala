package pipelines.socket

import akka.http.scaladsl.server.Route
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings}
import pipelines.users.jwt.Claims

/**
  * Exposes a means to create (or reuse) a socket for the authenticated user
  *
  * @param settings
  * @param handleSocket
  */
class SocketRoutes(settings: SecureRouteSettings, handleSocket: (Claims, ServerSocket) => Unit, cache: BaseSocketRoutes.SocketCache = new BaseSocketRoutes.SocketCache)
    extends BaseSocketRoutes(settings, cache)
    with SocketEndpoint
    with BaseCirceRoutes {

  def connectSocket: Route = {
    val wtf = implicitly[JsonResponse[String]]
    val authenticatedConnect = sockets.connect(wtf).request.flatMap { _ =>
      authenticated
    }

    authenticatedConnect { user =>
      val created  = s"${user.name} created at ${timestamp()}"
      val settings = SocketSettings(user.name, created)
      withSocketRoute(settings) {
        case (_, socket) => handleSocket(user, socket)
      }
    }
  }
  def connectAnySocket(user: Claims): Route = {
    val wtf = implicitly[JsonResponse[String]]
    sockets.connect(wtf).request { _ =>
      val created  = s"${user.name} created at ${timestamp()}"
      val settings = SocketSettings(user.name, created)
      withSocketRoute(settings) {
        case (_, socket) => handleSocket(user, socket)
      }
    }
  }
  def routes: Route = connectSocket
}
