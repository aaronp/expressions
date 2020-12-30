package expressions.client

import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp}
import org.http4s.client.Client
import org.http4s.{Header, Headers, Uri}

object RestClient {

  import scala.concurrent.ExecutionContext.Implicits.global
  private object Inst {
    object FML extends IOApp {
      val ce                                             = implicitly[ConcurrentEffect[IO]]
      override def run(args: List[String]): IO[ExitCode] = IO(ExitCode.Success)
    }

    import org.http4s.client.blaze._
    implicit val ce = FML.ce

    def asHttp4sReq(req: HttpRequest): org.http4s.Request[IO] = {
      val m = org.http4s.Method.fromString(req.method.name).toTry.get
      val headers = Headers(req.headers.toList.map {
        case (k, v) => Header(k, v)
      })

      val body = fs2.Stream.fromIterator(req.body.iterator)
      org.http4s.Request.apply(m, uri = Uri.fromString(req.url).toTry.get, headers = headers, body = body)
    }

    def apply(req: HttpRequest): IO[String] = {
      BlazeClientBuilder[IO](global).resource.use { client: Client[IO] =>
        // use `client` here and return an `IO`.
        // the client will be acquired and shut down
        // automatically each time the `IO` is run.
        val r = asHttp4sReq(req)

        //
        // SERIOUSLY!
        // no wonder people hate fucking scala.
        //
        // "How do I get the response back" ... "oh, we hide all that shit from you, unless you want o compile/fold a stream..."
        //

//        val dec = implicitly[EntityDecoder[IO, String]]
//        val dec = implicitly[EntityDecoder[IO, String]]
        client.expect[String](r)
      }
    }
  }

//  def sendJson(request: HttpRequest): Try[Json] = ??? //asTry(send(request))

  /**
    * Seriously -- I added a 'proxy' route to test out executing Http requests, and was setting that sttp was seeing POST requests
    * as GET requests. It looked to be within that library, 'cause switching to http4s client then worked as expected
    * @param request
    * @return
    */
  def send(request: HttpRequest) = {
    val io = Inst(request).attempt.map {
      case Left(err)   => HttpResponse(500, err.toString)
      case Right(body) => HttpResponse(200, body)
    }
    io.unsafeToFuture()
  }
//
//  def asSttpRequest(request: HttpRequest): RequestT[Identity, Either[String, String], Any] = {
//    import HttpMethod._
//
//    val reqUrl = uri"${request.url}"
//
//    val r: Request[Either[String, String], Any] = request.method match {
//      case GET     => basicRequest.get(reqUrl)
//      case POST    => basicRequest.post(reqUrl)
//      case PUT     => basicRequest.put(reqUrl)
//      case DELETE  => basicRequest.delete(reqUrl)
//      case HEAD    => basicRequest.head(reqUrl)
//      case OPTIONS => basicRequest.options(reqUrl)
//    }
//
//    val withBody = if (request.body.isEmpty) r else r.body(request.body)
//
//    request.headers.foldLeft(withBody) {
//      case (next, (k, v)) => next.header(k, v)
//    }
//  }
//
//  def asTry(response: Identity[Response[Either[String, String]]]): Try[Json] = {
//    if (!response.code.isSuccess) {
//      Failure(
//        new Exception(
//          s"Status code was ${response.code} : ${response.statusText}\n${response.body}"
//        )
//      )
//    } else {
//      response.body match {
//        case Left(err)    => Failure(new Exception(s"Error: $err"))
//        case Right(value) => io.circe.parser.parse(value).toTry
//      }
//    }
//  }
}
