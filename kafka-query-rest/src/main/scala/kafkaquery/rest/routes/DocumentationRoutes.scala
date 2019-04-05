package kafkaquery.rest.routes
import akka.http.scaladsl.server.Route
import endpoints.openapi.model.{OpenApi, OpenApiSchemas}
import kafkaquery.rest.Documentation

object DocumentationRoutes extends OpenApiSchemas with BaseRoutes {

  def route: Route = {
    val docEndpoint: Endpoint[Unit, OpenApi] = {
      endpoint(get(path / "openapi.json"), jsonResponse[OpenApi]())
    }
    docEndpoint.implementedBy(_ => Documentation.api)
  }
}
