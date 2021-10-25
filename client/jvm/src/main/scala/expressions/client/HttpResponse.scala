package expressions.client

import io.circe.Codec

case class HttpResponse(statusCode: Int, body: String)

object HttpResponse {
  given encoder : Codec[HttpResponse] = _root_.io.circe.generic.semiauto.deriveCodec[HttpResponse]
}
