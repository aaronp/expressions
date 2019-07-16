package pipelines.rest.openapi

import endpoints.openapi
import endpoints.openapi.model.{Info, OpenApi}
import pipelines.admin._
import pipelines.audit.{AuditEndpoints, VersionDetails}
import pipelines.auth.{AuthEndpoints, AuthModel, SetRolesForUserRequest, UserRoles}
import pipelines.core.GenericMessageResult
import pipelines.reactive.ContentType
import pipelines.reactive.repo.{ListRepoSourcesRequest, SourceEndpoints, TransformEndpoints}
import pipelines.users.{
  CreateUserRequest,
  CreateUserResponse,
  LoginEndpoints,
  LoginRequest,
  LoginResponse,
  UserEndpoints,
  UserReminderRequest,
  UserRoleEndpoints,
  UserStatusResponse
}

object OpenApiEncoder extends endpoints.openapi.model.OpenApiSchemas with endpoints.circe.JsonSchemas {
  implicit def requestSchema: JsonSchema[GenerateServerCertRequest] = JsonSchema(implicitly, implicitly)
}

/**
  * Generates OpenAPI documentation for the endpoints described in the `CounterEndpoints` trait.
  */
object Documentation          //
    extends openapi.Endpoints //
    with CirceAdapter         //
    with SourceEndpoints      //
    with TransformEndpoints   //
    with AdminEndpoints       //
    with LoginEndpoints       //
    with UserEndpoints        //
    with UserRoleEndpoints    //
    with AuthEndpoints        //
    with AuditEndpoints       // TODO !
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
      listUserRoles.getEndpoint(
        document(Some(VersionDetails(1, 2, "user") -> UserRoles(Map("user" -> Set("role")))))
      ),
      updateUserRoles.postEndpoint(
        document(SetRolesForUserRequest(1, "user", Set("roles"))),
        genericResp
      )
    )
  }
  def loginDocs: List[Documentation.DocumentedEndpoint] = {
    List(
      userLogin.loginEndpoint(
        document(LoginRequest("user", "pwd")),
        document(LoginResponse(true, Some("jwt"), Some("redirectx")))
      )
    )
  }

  def userDocs: List[Documentation.DocumentedEndpoint] = {
    List(
      createUser.createUserEndpoint(                                                        //
                                    document(CreateUserRequest("name", "email", "pwd")),    //
                                    document(CreateUserResponse(true, Some("token"), None)) //
      ),
      resetUser.resetUserEndpoint(
        document(UserReminderRequest("email")),
        genericResp
      ),
      confirmUser.confirmEndpoint,
      userStatus.statusEndpoint(document(UserStatusResponse("token", "user", "id", Set("roles"), Set("perms"), 123)))
    )
  }

  def adminEndpointDocs: List[Documentation.DocumentedEndpoint] = {
    List(
      generate.generateEndpoint(                                                    //
                                document(GenerateServerCertRequest("saveToPath")),  //
                                document(GenerateServerCertResponse("certificate")) //
      ),
      updatecert.updateEndpoint(                                                                  //
                                document(UpdateServerCertRequest("certificate", "save/to/path")), //
                                genericResp),
      seed.seedEndpoint(document(SetJWTSeedRequest("seed")), genericResp)
    )
  }

  def transformEndpoints: List[Documentation.DocumentedEndpoint] = {
    listTransforms.list(document(Option("sourceType"))) :: Nil
  }

  def repoEndpoints: List[Documentation.DocumentedEndpoint] = {
    val optionalType = Option(ContentType.of[Option[Int]])
    List(
      findSources.listEndpoint(document(ListRepoSourcesRequest(Map("meta" -> "data"), optionalType))),
      pushSource.pushEndpoint(document(Map("any" -> "data")), genericResp),
//      listTransforms.list(document(Option("type"))),
      types.list(document(Seq("a", "b")))
      //, repo.repoEndpoint(document(ListRepoSourcesRequest(optionalType)), document(ListRepoSourcesResponse(Seq(ListedDataSource("source", optionalType)))))
    )
  }

  def documentedEndpoints: List[Documentation.DocumentedEndpoint] = {
    repoEndpoints ++ adminEndpointDocs ++ authDocs ++ userAuthDocs ++ userDocs ++ loginDocs ++ transformEndpoints
  }

  lazy val api: OpenApi = openApi(
    Info(title = "OpenApi schema", version = "1.0.0")
  )(documentedEndpoints: _*)

}
