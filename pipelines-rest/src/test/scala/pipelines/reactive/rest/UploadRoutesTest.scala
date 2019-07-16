package pipelines.reactive.rest

import java.nio.file.Path

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import monix.execution.Scheduler
import pipelines.reactive.{PipelineService, UploadEvent}
import pipelines.rest.DevConfig
import pipelines.rest.routes.{BaseRoutesTest, SecureRouteSettings}
import pipelines.users.Claims
import pipelines.users.jwt.RichClaims._

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class UploadRoutesTest extends BaseRoutesTest {
  val settings: SecureRouteSettings = DevConfig.secureSettings
  val user                          = Claims.after(5.minutes).forUser("server").withId("some id")
  val jwt                           = user.asToken(settings.secret)

  "UploadRoutes" should {
    "POST /source/upload/xyz should upload data to the path 'xyz' source" in {

      withTempDir { uploadDir =>
        val service      = PipelineService()(Scheduler(executor))
        val uploadBuffer = ListBuffer[Path]()
        def onUpload(metadata: Map[String, String], uploads: Seq[UploadEvent]): String = {
          uploadBuffer ++= uploads.map(_.uploadPath)

          uploads.map(_.fileName).mkString(s"Uploaded ", ",", "")
        }
        val undertest = UploadRoutes(service, settings, uploadDir, onUpload)

        val content = """The content
                                                 |of the uploaded
                                                 |file""".stripMargin

        val multipartForm =
          Multipart.FormData(
            Multipart.FormData.BodyPart.Strict(
              "dave",
              HttpEntity(ContentTypes.`text/plain(UTF-8)`, content),
              Map("filename" -> "example.txt")
            ))

        val request = Post("/source/upload/id-of-a-path-push-source", multipartForm).withHeaders(Authorization(OAuth2BearerToken(jwt)))

        request ~> undertest.routes ~> check {
          responseText shouldBe "Uploaded example.txt"
          val List(upload) = uploadBuffer.toList

          import eie.io._
          upload.text shouldBe content
        }
      }
    }
  }
}
