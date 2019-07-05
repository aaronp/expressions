package pipelines.reactive.rest

import java.nio.file.Path

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives.{as, entity, extractExecutionContext, extractMaterializer, pathPrefix, _}
import akka.stream.scaladsl.FileIO
import eie.io._
import pipelines.reactive.PipelineService
import pipelines.rest.routes.{SecureRouteSettings, SecureRoutes}
import pipelines.users.Claims

import scala.concurrent.Future

case class UploadRoutes(pipelineService: PipelineService, secureSettings: SecureRouteSettings, uploadDir: Path) extends SecureRoutes(secureSettings) {

  //https://gist.github.com/jrudolph/08d0d28e1eddcd64dbd0
  def upload = {
    Directives.post {
      pathPrefix("source" / Segment / "upload") { name: String =>
        extractMaterializer { implicit materializer =>
          extractExecutionContext { implicit ec =>
            authenticated { claims: Claims =>
              entity(as[Multipart.FormData]) { (formdata: Multipart.FormData) =>
                val filesAndCount = formdata.parts.mapAsync(1) { p =>
                  println(s"Got part. name: ${p.name} filename: ${p.filename}")

                  val dir  = uploadDir.resolve(name).resolve(claims.name).mkDirs().resolve(p.filename.getOrElse(p.name))
                  val sink = FileIO.toPath(dir)
                  p.entity.dataBytes.runWith(sink).map { result =>
                    dir -> result.count
                  }
                }
                val all: Future[List[(java.nio.file.Path, Long)]] = filesAndCount.runFold(List[(java.nio.file.Path, Long)]()) {
                  case (state, next) => next +: state
                }

                val messageFuture: Future[String] = all.map { files =>
                  files.map(_._1.fileName).distinct.mkString("Uploaded ", ", ", "")

                }
                Directives.complete(messageFuture)
              }
            }
          }
        }
      }
    }
  }
}
