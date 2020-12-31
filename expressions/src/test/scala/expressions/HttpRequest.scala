package expressions

case class HttpRequest(method: String, url: String, headers: Map[String, String])

object HttpRequest {
  def get(url: String, headers: Map[String, String] = Map.empty)  = HttpRequest("GET", url, headers)
  def post(url: String, headers: Map[String, String] = Map.empty) = HttpRequest("POST", url, headers)

  implicit val codec = io.circe.generic.semiauto.deriveCodec[HttpRequest]
}
