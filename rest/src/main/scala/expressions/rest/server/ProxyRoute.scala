package expressions.rest.server

import expressions.client.{HttpRequest, HttpResponse, RestClient}
import expressions.rest.server.RestRoutes.taskDsl._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.Task
import zio.interop.catz._

object ProxyRoute {

  def apply(): HttpRoutes[Task] = proxy { in =>
    Task(RestClient.sendSync(in))
  }

  def proxy(makeRequest: HttpRequest => Task[HttpResponse]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "proxy" =>
        for {
          request <- req.as[HttpRequest]
          resp    <- makeRequest(request)
        } yield Response[Task](Status.Ok).withEntity(resp)
    }
  }
}
