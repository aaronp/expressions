package pipelines.users

trait UserSchemas extends endpoints.circe.JsonSchemas {

  implicit def LoginRequestSchema: JsonSchema[LoginRequest]   = JsonSchema(implicitly, implicitly)
  implicit def LoginResponseSchema: JsonSchema[LoginResponse] = JsonSchema(implicitly, implicitly)


}
