package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO}

object KafkaRoute {

  import RestRoutes.taskDsl._

  def apply(service: BusinessLogic.Service): HttpRoutes[Task] = start(service.start) <+> stop(service.stop) <+> running(service.running())

  def start(start: Config => Task[String]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "kafka" / "start" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(ConfigFactory.load()).resolve())
          id     <- start(config)
        } yield Response[Task](Status.Ok).withEntity(id)
    }
  }

  def stop(stop: String => Task[Boolean]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case POST -> Root / "kafka" / "stop" / id => stop(id).map(Response[Task](Status.Ok).withEntity)
    }
  }

  def running(listRunning: UIO[List[String]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "running" => listRunning.map(Response[Task](Status.Ok).withEntity)
    }
  }
}
