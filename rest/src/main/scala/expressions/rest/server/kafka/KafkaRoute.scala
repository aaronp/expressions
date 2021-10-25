package expressions.rest.server.kafka

import cats.implicits._
import com.typesafe.config.Config
import expressions.client.kafka.{ConsumerStats, StartedConsumer}
import expressions.rest.server.LoadConfig
import expressions.rest.server.RestRoutes.taskDsl._
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO}

object KafkaRoute {
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  def apply(loadCfg: LoadConfig, service: KafkaSink.Service): HttpRoutes[Task] = {
    start(loadCfg, service.start) <+> stop(service.stop) <+> running(service.running()) <+> stats(service.stats)
  }

  def start(loadCfg: LoadConfig, execute: Config => Task[String]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case POST -> "kafka" /: "start" /: pathToConfig =>
        for {
          config <- loadCfg.at(pathToConfig.segments.map(_.encoded).toList)
          id     <- execute(config)
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
