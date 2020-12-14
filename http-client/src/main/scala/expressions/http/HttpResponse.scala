package expressions.http

case class HttpResponse(statusCode: Int, body: String)

object HttpResponse {
  implicit val encoder = io.circe.generic.semiauto.deriveCodec[HttpResponse]
}
