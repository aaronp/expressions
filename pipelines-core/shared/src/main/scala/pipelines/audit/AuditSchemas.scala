package pipelines.audit

import pipelines.auth.SetRolesForUserRequest

trait AuditSchemas extends endpoints.circe.JsonSchemas {

  implicit def VersionDetailsSchema: JsonSchema[VersionDetails]                 = JsonSchema(implicitly, implicitly)
  implicit def AuditVersionSchema: JsonSchema[AuditVersion]                     = JsonSchema(implicitly, implicitly)
  implicit def SetRolesForUserRequestSchema: JsonSchema[SetRolesForUserRequest] = JsonSchema(implicitly, implicitly)

}
