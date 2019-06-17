package pipelines.users

trait AuthSchemas extends endpoints.circe.JsonSchemas {

  implicit def AuthModelSchema: JsonSchema[AuthModel] = JsonSchema(implicitly, implicitly)
  implicit def UserAuthSchema: JsonSchema[UserAuth]   = JsonSchema(implicitly, implicitly)

}
