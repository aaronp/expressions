package pipelines.reactive.rest

import java.nio.file.Path
import java.util.Base64

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Directives.{as, entity, extractExecutionContext, extractMaterializer, pathPrefix, _}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.FileIO
import eie.io._
import pipelines.reactive.{PipelineService, UploadEvent}
import pipelines.rest.RestMain
import pipelines.rest.routes.{SecureRouteSettings, SecureRoutes}
import pipelines.users.Claims

import scala.concurrent.Future

/**
  * A route for processing multi-part uploads to create/push to [[UploadEvent]] data sources
  *
  * @param pipelineService
  * @param secureSettings
  * @param uploadDir
  * @param handleUpload
  */
case class UploadRoutes(pipelineService: PipelineService, secureSettings: SecureRouteSettings, uploadDir: Path, handleUpload: (Map[String, String], Seq[UploadEvent]) => String)
    extends SecureRoutes(secureSettings) {

  def routes: Route = upload

  def safeName(name: String) = {
    Base64.getUrlEncoder.encodeToString(name.getBytes("UTF-8"))
  }

  //https://gist.github.com/jrudolph/08d0d28e1eddcd64dbd0
  def upload: Route = {
    Directives.post {
      extractUri { uri =>
        pathPrefix("source" / "upload" / Segment) { name: String =>
          extractMaterializer { implicit materializer =>
            extractExecutionContext { implicit ec =>
              authenticated { claims: Claims =>
                val metadata: Map[String, String] = RestMain.queryParamsForUri(uri, claims)

                entity(as[Multipart.FormData]) { (formdata: Multipart.FormData) =>
                  val filesByFileName = formdata.parts.mapAsync(1) { p =>
                    logger.debug(s"Got part. name: ${p.name} filename: ${p.filename}")

                    /**
                      * upload to:
                      *
                      * <uploadDir>/<name>/<user>
                      */
                    val fileName = p.filename.getOrElse(p.name)
                    val filePath = uploadDir.resolve(safeName(name)).resolve(safeName(claims.name)).resolve(safeName(fileName))

                    filePath.mkParentDirs()

                    logger.info(s"Uploading to: ${filePath.toAbsolutePath}")

                    val sink = FileIO.toPath(filePath)
                    p.entity.dataBytes.runWith(sink).map { result =>
                      fileName -> (filePath, result.count)
                    }
                  }
                  val all: Future[Map[String, Path]] = filesByFileName.runFold(Map[String, java.nio.file.Path]()) {
                    case (byFileName, (fileName, (path, _))) =>
                      byFileName.get(fileName) match {
                        case Some(existingPath) =>
                          if (existingPath == path) {
                            byFileName
                          } else {
                            sys.error(s"Multiple files uploaded w/ the same name: $fileName")
                          }
                        case None =>
                          byFileName.updated(fileName, path)
                      }
                  }

                  val messageFuture = all.map { filesByName =>
                    val uploads = filesByName.map {
                      case (fileName, path) => UploadEvent(claims, path, fileName)
                    }
                    handleUpload(metadata, uploads.toSeq)
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
}
