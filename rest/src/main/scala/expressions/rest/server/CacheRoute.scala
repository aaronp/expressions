package expressions.rest.server

import cats.implicits._
import io.circe.Json
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Request, Response, Status}
import zio.interop.catz._
import zio.{Ref, Task, ZIO}

/**
  * just a way users can CRUD <namespace>/<collection>/<id> data
  */
object CacheRoute {

  import RestRoutes.taskDsl._

  def apply(): ZIO[Any, Nothing, HttpRoutes[Task]] = Ref.make(Map.empty[String, Json]).map { cache =>
    postRoute(cache) <+> getRoute(cache)
  }

  def postRoute(cache: Ref[Map[String, Json]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ (POST -> "cache" /: theRest) =>
        asJson(req).flatMap { json =>
          val key = theRest.segments.mkString("/")
          cache.modify { byPath =>
            val response = if (byPath.contains(key)) {
              Response[Task](Status.Ok)
            } else {
              Response[Task](Status.Created)
            }
            response -> byPath.updated(key, json)
          }
        }
    }
  }

  private def asJson(req: Request[Task]) = {
    req.as[Json].either.flatMap {
      case Left(_) =>
        req.bodyText.compile.string.map(Json.fromString)
      case Right(json) => ZIO.succeed(json)

    }
  }

  def getRoute(cache: Ref[Map[String, Json]]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> "cache" /: theRest =>
        cache.get.map { byPath =>
          val key = theRest.segments.mkString("/")
          byPath.get(key) match {
            case Some(value) =>
              Response[Task](Status.Ok).withEntity(value)
            case None =>
              Response[Task](Status.Gone)
          }
        }
    }
  }
}
