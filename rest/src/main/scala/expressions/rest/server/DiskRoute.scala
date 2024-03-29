package expressions.rest.server

import cats.implicits._
import com.typesafe.config.Config
import expressions.rest.server.Disk.ListEntry
import io.circe.Json
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, ZIO}

/**
  * just a way users can CRUD <namespace>/<collection>/<id> data
  */
object DiskRoute {

  import RestRoutes.taskDsl._

  def apply(rootConfig: Config): ZIO[Any, Throwable, HttpRoutes[Task]] = {
    Disk(rootConfig).map(svc => apply(svc))
  }

  def apply(service: Disk.Service): HttpRoutes[Task] = getRoute(service) <+> postRoute(service) <+> listRoute(service.list) <+> deleteRoute(service)

  def postRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ (POST -> "store" /: theRest) =>
        for {
          body: String <- req.bodyText.compile.string
          created      <- service.write(theRest.segments.map(_.encoded), body)
        } yield {
          if (created) Response[Task](Status.Created) else Response[Task](Status.Ok)
        }
    }
  }

  def getRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> "store" /: "get" /: theRest =>
        service.read(theRest.segments.map(_.encoded)).map {
          case Some(value) =>
            Response[Task](Status.Ok).withEntity[String](value)
          case None =>
            Response[Task](Status.Ok).withEntity[String]("")
        }
    }
  }

  def deleteRoute(service: Disk.Service): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case DELETE -> "store" /: theRest =>
        service.remove(theRest.segments.map(_.encoded)).map { _ =>
          Response[Task](Status.Ok)
        }
    }
  }

  def listRoute(service: Seq[String] => Task[Seq[ListEntry]]): HttpRoutes[Task] = {

    import org.http4s.circe.CirceEntityCodec._
    HttpRoutes.of[Task] {
      case GET -> "store" /: "list" /: theRest =>
        service(theRest.segments.map(_.encoded)).map { values =>
          val entries: Seq[String] = values.map {
            case Left(fullPath) => fullPath.mkString("/")
            case Right(path)    => (theRest.segments :+ path).mkString("/")
          }
          Response[Task](Status.Ok).withEntity(entries)
        }
    }
  }
}
