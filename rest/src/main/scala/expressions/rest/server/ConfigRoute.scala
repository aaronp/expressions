package expressions.rest.server

import cats.implicits._
import com.typesafe.config.ConfigFactory
import expressions.franz.FranzConfig
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.Task
import zio.interop.catz._

object ConfigRoute {

  import RestRoutes.taskDsl._

  def apply(service: Disk.Service): HttpRoutes[Task] = postRoute(service) <+> getRoute(service)

  def postRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "topic" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(ConfigFactory.load()).resolve())
        } yield {
          val fc = FranzConfig(config)
          Response[Task](Status.Ok).withEntity(fc.topic)
        }
    }
  }

  def getRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> "store" /: theRest =>
        service.read(theRest.toList).map {
          case Some(value) => Response[Task](Status.Ok).withEntity(value)
          case None        => Response[Task](Status.Gone)
        }
    }
  }
}
