package pipelines.socket

trait SocketSchemas extends endpoints.circe.JsonSchemas {
  implicit def AddressedTextMessageSchema: JsonSchema[AddressedTextMessage]     = JsonSchema(implicitly, implicitly)
  implicit def AddressedBinaryMessageSchema: JsonSchema[AddressedBinaryMessage] = JsonSchema(implicitly, implicitly)

}
