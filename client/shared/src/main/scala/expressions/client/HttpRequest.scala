package expressions.client

import HttpMethod._
case class HttpRequest(method: HttpMethod, url: String, headers: Map[String, String], body: Array[Byte]) {
  def withHeader(key: String, value: String): HttpRequest = copy(headers = headers.updated(key, value))
  def withBody(newBody: String): HttpRequest              = copy(body = newBody.getBytes("UTF-8"))
}

object HttpRequest {
  def get(url: String, headers: Map[String, String] = Map.empty)    = HttpRequest(GET, url, headers, Array.empty)
  def post(url: String, headers: Map[String, String] = Map.empty)   = HttpRequest(POST, url, headers, Array.empty)
  def put(url: String, headers: Map[String, String] = Map.empty)    = HttpRequest(PUT, url, headers, Array.empty)
  def delete(url: String, headers: Map[String, String] = Map.empty) = HttpRequest(DELETE, url, headers, Array.empty)

  implicit val encoder = io.circe.generic.semiauto.deriveCodec[HttpRequest]
}
