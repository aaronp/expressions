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

  def apply(service: Disk.Service): HttpRoutes[Task] = getRoute(service) <+> postRoute(service) <+> listRoute(service.list)

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
      case GET -> "store" /: "get" /: theRest =>
        service.read(theRest.toList).map {
          case Some(value) => Response[Task](Status.Ok).withEntity[String](value)
          case None        =>
//
            //            Response[Task](Status.Gone)
            Response[Task](Status.Ok).withEntity[String]("")
        }
    }
  }
  def listRoute(service: Seq[String] => Task[Seq[ListEntry]]): HttpRoutes[Task] = {

    import org.http4s.circe.CirceEntityCodec._
    HttpRoutes.of[Task] {
      case GET -> "store" /: "list" /: theRest =>
        service(theRest.toList).map { values =>
          val entries: Seq[String] = values.map {
            case Left(fullPath) => fullPath.mkString("/")
            case Right(path)    => (theRest.toList :+ path).mkString("/")
          }
          Response[Task](Status.Ok).withEntity(entries)
        }
    }
  }
}
