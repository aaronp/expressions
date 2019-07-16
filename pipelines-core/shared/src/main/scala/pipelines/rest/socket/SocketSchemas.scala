package pipelines.rest.socket

trait SocketSchemas extends endpoints.circe.JsonSchemas {
  implicit def SocketSubscribeRequestSchema: JsonSchema[SocketSubscribeRequest]     = JsonSchema(implicitly, implicitly)
  implicit def SocketSubscribeResponseSchema: JsonSchema[SocketSubscribeResponse]     = JsonSchema(implicitly, implicitly)

  implicit def AddressedTextMessageSchema: JsonSchema[AddressedTextMessage]     = JsonSchema(implicitly, implicitly)
  implicit def AddressedBinaryMessageSchema: JsonSchema[AddressedBinaryMessage] = JsonSchema(implicitly, implicitly)

}
