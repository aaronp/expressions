package expressions.client

import expressions.client.HttpMethod._
import io.circe.Decoder.Result
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import io.circe.{Codec, DecodingFailure, HCursor, Json}

import java.util.Base64
case class HttpRequest(method: HttpMethod, url: String, headers: Map[String, String], body: Array[Byte]) {

  override def equals(obj: Any): Boolean = {
    obj match {
      case other: HttpRequest =>
        other.method == method &&
          other.url == url &&
          other.headers == headers &&
          other.body.size == body.size &&
          other.bodyAsBase64 == bodyAsBase64
      case _ => false
    }
  }

  override def toString() = {
    s"""HttpRequest.${method}{
      |  url : $url,
      |  ${headers.size} headers : ${headers.mkString(";")},
      |  body : ${new String(body)}
      |}""".stripMargin
  }
  def withHeader(key: String, value: String): HttpRequest = copy(headers = headers.updated(key, value))
  def withBody(newBody: String): HttpRequest              = copy(body = newBody.getBytes("UTF-8"))

  def bodyAsBase64: String = Base64.getEncoder.encodeToString(body)
  def bodyAsString         = new String(body, "UTF-8")
  def bodyAsJson           = parse(bodyAsString).toTry
}

object HttpRequest {
  def get(url: String, headers: Map[String, String] = Map.empty)    = HttpRequest(GET, url, headers, Array.empty)
  def post(url: String, headers: Map[String, String] = Map.empty)   = HttpRequest(POST, url, headers, Array.empty)
  def put(url: String, headers: Map[String, String] = Map.empty)    = HttpRequest(PUT, url, headers, Array.empty)
  def delete(url: String, headers: Map[String, String] = Map.empty) = HttpRequest(DELETE, url, headers, Array.empty)

  implicit object codec extends Codec[HttpRequest] {
    override def apply(input: HttpRequest): Json = {
      import input._
      Json.obj(
        "method"  -> method.asJson,
        "url"     -> url.asJson,
        "headers" -> headers.asJson,
        "body"    -> bodyAsJson.getOrElse(Json.fromString(bodyAsBase64))
      )
    }

    override def apply(c: HCursor): Result[HttpRequest] = {
      def bodyBytes: Either[DecodingFailure, Array[Byte]] = {
        val b = c.downField("body")
        b.as[Json].map(_.noSpaces.getBytes("UTF-8")).orElse {
          b.as[String].map { base64Bytes =>
            Base64.getDecoder.decode(base64Bytes)
          }
        }
      }
      for {
        method  <- c.downField("method").as[HttpMethod]
        url     <- c.downField("url").as[String]
        headers <- c.downField("headers").as[Map[String, String]]
        body    <- bodyBytes
      } yield HttpRequest(method, url, headers, body)
    }
  }
}
