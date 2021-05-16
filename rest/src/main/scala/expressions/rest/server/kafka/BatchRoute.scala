package expressions.rest.server.kafka

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import expressions.client.kafka.{ConsumerStats, StartedConsumer}
import expressions.rest.server.{LoadConfig, RestRoutes}
import expressions.rest.server.RestRoutes.taskDsl._
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO, URIO, ZEnv, ZIO}

object BatchRoute {
  import RestRoutes.Resp

  def make(loadCfg: LoadConfig): URIO[ZEnv, HttpRoutes[Task]] =
    for {
      batchSink <- BatchSink.make
      env       <- ZIO.environment[ZEnv]
    } yield apply(loadCfg, batchSink, env)

  def apply(loadCfg: LoadConfig, service: KafkaSink.Service, defaultEnv: ZEnv): HttpRoutes[Task] = {
    start(loadCfg, service.start) <+> stop(service.stop) <+> running(service.running()) <+> stats(service.stats) <+> testRoute(BatchCheck(loadCfg.rootConfig, defaultEnv))
  }

  def testRoute(handler: BatchCheckRequest => ZIO[Any, Throwable, Resp]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "batch" / "test" =>
        for {
          dto      <- req.as[BatchCheckRequest]
          response <- handler(dto)
        } yield response
    }
  }

  def start(loadCfg: LoadConfig, execute: Config => Task[String]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case POST -> "batch" /: "start" /: pathToConfig =>
        for {
          config <- loadCfg.at(pathToConfig.segments.map(_.encoded).toList)
          id     <- execute(config)
        } yield Response[Task](Status.Ok).withEntity(id)
    }

  }

  def stop(stop: String => Task[Boolean]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case POST -> Root / "batch" / "stop" / id => stop(id).map(r => Response[Task](Status.Ok).withEntity(r))
    }
  }

  def running(listRunning: UIO[List[StartedConsumer]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "batch" / "running" => listRunning.map(r => Response[Task](Status.Ok).withEntity(r.asJson))
    }
  }

  def stats(getStats: String => Task[Option[ConsumerStats]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "batch" / "stats" / id =>
        getStats(id).map { found =>
          Response[Task](Status.Ok).withEntity(found)
        }
    }
  }
}
