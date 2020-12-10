package expressions.client

case class HttpRequest(method: String, url: String, headers: Map[String, String])

object HttpRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[HttpRequest]
}
