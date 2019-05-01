package pipelines.rest.openapi

import endpoints.openapi
import endpoints.openapi.model.{Info, OpenApi}
import io.circe.Json
import pipelines.admin._
import pipelines.core.GenericMessageResult
import pipelines.reactive.ContentType
import pipelines.reactive.repo.{ListRepoSourcesRequest, ListRepoSourcesResponse, ListedDataSource, SourceRepoEndpoints}

object OpenApiEncoder extends endpoints.openapi.model.OpenApiSchemas with endpoints.circe.JsonSchemas {
  implicit def requestSchema: JsonSchema[GenerateServerCertRequest] = JsonSchema(implicitly, implicitly)
}

/**
  * Generates OpenAPI documentation for the endpoints described in the `CounterEndpoints` trait.
  */
object Documentation //
    extends openapi.Endpoints //
    with CirceAdapter        //
    with SourceRepoEndpoints //
    with AdminEndpoints      //
    with openapi.JsonSchemaEntities {

  import OpenApiEncoder.JsonSchema._

  val genericResp: Documentation.DocumentedJsonSchema = document(GenericMessageResult("a response message"))

  def adminEndpointDocs: List[Documentation.DocumentedEndpoint] = {
    List(
      generate.generateEndpoint( //
                                document(GenerateServerCertRequest("saveToPath")), //
                                document(GenerateServerCertResponse("certificate")) //
      ),
      updatecert.updateEndpoint( //
                                document(UpdateServerCertRequest("certificate", "save/to/path")), //
                                genericResp),
      seed.seedEndpoint(document(SetJWTSeedRequest("seed")), genericResp)
    )
  }

  def repoEndpoints: List[Documentation.DocumentedEndpoint] = {
    val optionalType = Option(ContentType.of[Option[Int]])
    List(
      sources.list(document(ListRepoSourcesRequest(optionalType))),
      transforms.list(document(Option("type"))),
      types.list(document(Seq("a", "b"))),
      repo.repoEndpoint(document(ListRepoSourcesRequest(optionalType)), document(ListRepoSourcesResponse(Seq(ListedDataSource("source", optionalType)))))
    )
  }

  def documentedEndpoints: List[Documentation.DocumentedEndpoint] = {
    repoEndpoints ++ adminEndpointDocs
  }

  lazy val api: OpenApi = openApi(
    Info(title = "OpenApi schema", version = "1.0.0")
  )(documentedEndpoints: _*)

}
