package expressions.client

import io.circe.Json

case class TransformRequest(script : String, input : Json,
                            key: Json = Json.fromString(""),
                            timestamp: Long = 0,
                            headers: Map[String, String] = Map.empty,
                            topic : String = "",
                            offset : Long = 0,
                            partition : Int = 0)

object TransformRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[TransformRequest]
}

case class TransformResponse(result : Json, messages : Option[String])

object TransformResponse {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[TransformResponse]
}