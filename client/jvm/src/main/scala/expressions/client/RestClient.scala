package expressions.client

import io.circe.Json
import sttp.client.{HttpURLConnectionBackend, Identity, Request, Response, UriContext, basicRequest}

import scala.util.{Failure, Try}

object RestClient {
  private val backend = HttpURLConnectionBackend()

  def sendJson(request: HttpRequest): Try[Json] = asTry(send(request))

  def send(request: HttpRequest): Response[Either[String, String]] = asSttpRequest(request).send(backend)

  def asSttpRequest(request: HttpRequest) = {
    import HttpMethod._

    val reqUrl = uri"${request.url}"

    val r: Request[Either[String, String], Any] = request.method match {
      case GET     => basicRequest.get(reqUrl)
      case POST    => basicRequest.post(reqUrl)
      case PUT     => basicRequest.put(reqUrl)
      case DELETE  => basicRequest.delete(reqUrl)
      case HEAD    => basicRequest.head(reqUrl)
      case OPTIONS => basicRequest.options(reqUrl)
    }

    val withBody = if (request.body.isEmpty) r else r.body(request.body)

    request.headers.foldLeft(withBody) {
      case (next, (k, v)) => next.header(k, v)
    }
  }

  def asTry(response: Identity[Response[Either[String, String]]]): Try[Json] = {
    if (!response.code.isSuccess) {
      Failure(
        new Exception(
          s"Status code was ${response.code} : ${response.statusText}\n${response.body}"
        )
      )
    } else {
      response.body match {
        case Left(err)    => Failure(new Exception(s"Error: $err"))
        case Right(value) => io.circe.parser.parse(value).toTry
      }
    }
  }
}
