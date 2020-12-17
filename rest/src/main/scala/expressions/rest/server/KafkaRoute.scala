package expressions.rest.server

import cats.implicits._
import com.typesafe.config.ConfigFactory
import expressions.franz.FranzConfig
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.Task
import io.circe.syntax._
import zio.interop.catz._

object KafkaRoute {

  import RestRoutes.taskDsl._

  def apply(service: Disk.Service): HttpRoutes[Task] = resolveMappingsRoute(service) <+> listMappingsRoute()

  def resolveMappingsRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "mappings" / "resolve" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(ConfigFactory.load()).resolve())
        } yield {
          val json = MappingConfig(config).mappings.asJson
          Response[Task](Status.Ok).withEntity(json)
        }
    }
  }

  def listMappingsRoute(): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "mappings" / "list" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(ConfigFactory.load()).resolve())
        } yield {
          val json = MappingConfig(config).mappings.asJson
          Response[Task](Status.Ok).withEntity(json)
        }
    }
  }
}
