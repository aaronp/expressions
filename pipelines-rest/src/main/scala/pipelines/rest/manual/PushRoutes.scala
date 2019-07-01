package pipelines.rest.manual

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import pipelines.manual.PushEndpoints
import pipelines.reactive.{DataSource, MetadataCriteria, PipelineService}
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}
import pipelines.socket.SocketSchemas
import pipelines.users.jwt.Claims

case class PushRoutes(service: PipelineService, secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with BaseCirceRoutes
    with PushEndpoints
    with SocketSchemas {

  def cancelRoute: Route = {
    val authCancel: Directive[(Claims, Uri.Query)] = push.pushEndpointCancel.request.flatMap { _ =>
      authWithQuery
    }
    authCancel {
      case (user, query) =>
        val metadata                   = query.toMap.updated("user", user.name)
        val criteria: MetadataCriteria = MetadataCriteria(metadata)
        val found: Seq[DataSource]     = service.sources.find(criteria)

        // TODO - cancel
        ???
        val pec: Endpoint[Unit, Unit] = push.pushEndpointCancel
        pec.response(Unit)
    }
  }
  def pushPostRoute = {
    push.pushEndpointPost.implementedBy { _ =>
      ???
    }
  }
  def pushGetRoute = {
    push.pushEndpointGet.implementedBy { x =>
      ???
    }
  }
  def errorRoute = {
    push.pushEndpointError.implementedBy { msgOpt =>
      val msg = msgOpt.getOrElse("User-invoked error")
      ???

    }
  }
  def routes: Route = cancelRoute ~ pushPostRoute ~ pushGetRoute ~ errorRoute
}
