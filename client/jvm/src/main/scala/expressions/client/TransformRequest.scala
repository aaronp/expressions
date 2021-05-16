package expressions.client

import io.circe.{Decoder, Json, Codec}

import scala.util.Try

case class TransformRequest(script: String,
                            input: Json,
                            key: Json = Json.fromString(""),
                            timestamp: Long = 0,
                            headers: Map[String, String] = Map.empty,
                            topic: String = "",
                            offset: Long = 0,
                            partition: Int = 0)

object TransformRequest {
  given codec : Codec[TransformRequest] = io.circe.generic.semiauto.deriveCodec[TransformRequest]
}

case class TransformResponse(result: Json, success: Boolean = true, messages: List[String] = Nil) {
  def as[A: Decoder]: Try[A] = result.as[A].toTry
}

object TransformResponse {
  given codec : Codec[TransformResponse] = io.circe.generic.semiauto.deriveCodec[TransformResponse]
}
