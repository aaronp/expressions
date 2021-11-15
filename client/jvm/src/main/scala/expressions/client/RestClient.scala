package expressions.client

import cats.effect.*
import org.http4s.client.Client
import org.http4s.{Header, Headers, Uri}
import org.typelevel.ci.CIString

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.Future

object RestClient {
  def sendSync(request: HttpRequest) = {
    val response = request.method match {
      case HttpMethod.GET     => requests.get(request.url, headers = request.headers)
      case HttpMethod.POST    => requests.post(request.url, headers = request.headers, data = request.body)
      case HttpMethod.PUT     => requests.put(request.url, headers = request.headers, data = request.body)
      case HttpMethod.DELETE  => requests.delete(request.url, headers = request.headers, data = request.body)
      case HttpMethod.HEAD    => requests.head(request.url, headers = request.headers, data = request.body)
      case HttpMethod.OPTIONS => requests.options(request.url, headers = request.headers, data = request.body)
    }
    HttpResponse(response.statusCode, response.data.toString)
  }

}
