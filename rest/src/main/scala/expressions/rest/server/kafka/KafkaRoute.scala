package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import expressions.client.kafka.{ConsumerStats, StartedConsumer}
import org.http4s.{HttpRoutes, Response, Status}
import zio.{Task, UIO}
import expressions.rest.server.RestRoutes.taskDsl._
import zio.interop.catz._
import org.http4s.circe.CirceEntityCodec._
import cats.implicits._
import io.circe.syntax.EncoderOps

object KafkaRoute {

  def apply(defaultConfig: Config, service: KafkaSink.Service): HttpRoutes[Task] = {
    start(defaultConfig, service.start) <+> stop(service.stop) <+> running(service.running()) <+> stats(service.stats)
  }

  def start(defaultConfig: Config, start: Config => Task[String]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "kafka" / "start" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(defaultConfig).resolve())
          id     <- start(config)
        } yield Response[Task](Status.Ok).withEntity(id)
    }
  }

  def stop(stop: String => Task[Boolean]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case POST -> Root / "kafka" / "stop" / id => stop(id).map(r => Response[Task](Status.Ok).withEntity(r))
    }
  }

  def running(listRunning: UIO[List[StartedConsumer]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "running" => listRunning.map(r => Response[Task](Status.Ok).withEntity(r.asJson))
    }
  }

  def stats(getStats: String => Task[Option[ConsumerStats]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "stats" / id =>
        getStats(id).map { found =>
          Response[Task](Status.Ok).withEntity(found)
        }
    }
  }
}
