package pipelines.users

import pipelines.audit.VersionDetails
import pipelines.auth.UserRoles

trait UserSchemas extends endpoints.circe.JsonSchemas {

  implicit def UserReminderRequestSchema: JsonSchema[UserReminderRequest]   = JsonSchema(implicitly, implicitly)

  implicit def LoginRequestSchema: JsonSchema[LoginRequest]   = JsonSchema(implicitly, implicitly)
  implicit def LoginResponseSchema: JsonSchema[LoginResponse] = JsonSchema(implicitly, implicitly)

  implicit def createUserRequestSchema: JsonSchema[CreateUserRequest]   = JsonSchema(implicitly, implicitly)
  implicit def createUserResponseSchema: JsonSchema[CreateUserResponse] = JsonSchema(implicitly, implicitly)

  implicit def userStatusSchema: JsonSchema[UserStatusResponse] = JsonSchema(implicitly, implicitly)

  implicit def auditVersionTupleSchema: JsonSchema[Option[(VersionDetails, UserRoles)]] = JsonSchema(implicitly, implicitly)
}
