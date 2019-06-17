package pipelines.rest.openapi

import endpoints.openapi
import endpoints.openapi.model.{Info, OpenApi}
import pipelines.admin._
import pipelines.audit.AuditEndpoints
import pipelines.core.GenericMessageResult
import pipelines.reactive.ContentType
import pipelines.reactive.repo.{ListRepoSourcesRequest, SourceRepoEndpoints}
import pipelines.users.{AuthEndpoints, AuthModel, UserAuthEndpoints, UserEndpoints}

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
    with UserEndpoints       //
    with UserAuthEndpoints   //
    with AuthEndpoints       //
    with AuditEndpoints      // TODO !
    with openapi.JsonSchemaEntities {

  import OpenApiEncoder.JsonSchema._

  val genericResp: Documentation.DocumentedJsonSchema = document(GenericMessageResult("a response message"))

  def authDocs: List[Documentation.DocumentedEndpoint] = {
    val authModelJson = document(AuthModel(Map("role" -> Set("perm"))))
    List(
      updateAuth.updateEndpoint(
        authModelJson,
        genericResp
      ),
      getAuth.getEndpoint(
        authModelJson
      )
    )
  }
  def userAuthDocs: List[Documentation.DocumentedEndpoint] = {
    List(
      getUserAuth.getEndpoint(
        genericResp
      ),
      updateUserAuth.postEndpoint(
        document(AuthModel(Map("role" -> Set("perm")))),
        genericResp
      )
    )
  }

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
      types.list(document(Seq("a", "b")))
      //, repo.repoEndpoint(document(ListRepoSourcesRequest(optionalType)), document(ListRepoSourcesResponse(Seq(ListedDataSource("source", optionalType)))))
    )
  }

  def documentedEndpoints: List[Documentation.DocumentedEndpoint] = {
    repoEndpoints ++ adminEndpointDocs ++ authDocs ++ userAuthDocs
  }

  lazy val api: OpenApi = openApi(
    Info(title = "OpenApi schema", version = "1.0.0")
  )(documentedEndpoints: _*)

}
