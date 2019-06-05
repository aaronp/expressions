package pipelines.rest.openapi

import akka.http.scaladsl.server.Route
import endpoints.akkahttp.server
import endpoints.akkahttp.server.JsonSchemaEntities
import endpoints.openapi.model.{OpenApi, OpenApiSchemas}

//with endpoints.circe.JsonSchemas with StrictLogging
object DocumentationRoutes extends OpenApiSchemas with server.Endpoints with JsonSchemaEntities {

  def route: Route = {
    val docEndpoint: Endpoint[Unit, OpenApi] = {
      endpoint(get(path / "openapi.json"), jsonResponse[OpenApi]())
    }
    docEndpoint.implementedBy(_ => Documentation.api)
  }
}
