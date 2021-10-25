package expressions.client

import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp}
import org.http4s.client.Client
import org.http4s.{Header, Headers, Uri}

import scala.concurrent.Future

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
      BlazeClientBuilder[IO](Implicits.global).resource.use { client: Client[IO] =>
        // use `client` here and return an `IO`.
        // the client will be acquired and shut down
        // automatically each time the `IO` is run.
        val r = asHttp4sReq(req)

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
  def send(request: HttpRequest): Future[HttpResponse] = {
    sendIO(request).unsafeToFuture()
  }

  def sendIO(request: HttpRequest): IO[HttpResponse] = {
    Inst(request).attempt.map {
      case Left(err)   => HttpResponse(500, err.toString)
      case Right(body) => HttpResponse(200, body)
    }
  }

}
