package pipelines.auth

trait AuthSchemas extends endpoints.circe.JsonSchemas {

  implicit def AuthModelSchema: JsonSchema[AuthModel] = JsonSchema(implicitly, implicitly)
  implicit def UserAuthSchema: JsonSchema[SetRolesForUserRequest]   = JsonSchema(implicitly, implicitly)

}
