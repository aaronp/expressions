package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import zio.interop.catz._
import org.http4s.{HttpRoutes, Response, Status}
import zio.{Task, UIO}
import RestRoutes.taskDsl._
import expressions.client.kafka.StartedConsumer

object KafkaRoute {

  def apply(service: KafkaSink.Service): HttpRoutes[Task] = start(service.start) <+> stop(service.stop) <+> running(service.running())

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
      case POST -> Root / "kafka" / "stop" / id => stop(id).map(r => Response[Task](Status.Ok).withEntity(r.asJson))
    }
  }

  def running(listRunning: UIO[List[StartedConsumer]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "running" => listRunning.map(r => Response[Task](Status.Ok).withEntity(r.asJson))
    }
  }
}
