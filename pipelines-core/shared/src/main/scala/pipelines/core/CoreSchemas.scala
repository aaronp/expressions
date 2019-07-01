package pipelines.core

trait CoreSchemas extends endpoints.circe.JsonSchemas {
  implicit def GenericMessageResultSchema: JsonSchema[GenericMessageResult] = JsonSchema(implicitly, implicitly)

}
