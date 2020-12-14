package expressions.rest.server

import cats.implicits._
import com.typesafe.config.Config
import org.http4s.{HttpRoutes, Response, Status}
import zio.{Task, ZIO}
import zio.interop.catz._

/**
  * just a way users can CRUD <namespace>/<collection>/<id> data
  */
object DiskRoute {

  import RestRoutes.taskDsl._

  def apply(rootConfig: Config): ZIO[Any, Throwable, HttpRoutes[Task]] = {
    Disk(rootConfig).map(svc => apply(svc))
  }

  def apply(service: Disk.Service): HttpRoutes[Task] = postRoute(service) <+> getRoute(service)

  def postRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ (POST -> "store" /: theRest) =>
        for {
          body: String <- req.bodyText.compile.string
          created      <- service.write(theRest.toList, body)
        } yield {
          if (created) Response[Task](Status.Created) else Response[Task](Status.Ok)
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
