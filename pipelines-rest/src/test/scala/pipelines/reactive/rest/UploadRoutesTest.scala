package pipelines.reactive.rest

import monix.execution.Scheduler
import org.scalatest.WordSpec
import pipelines.reactive.PipelineService
import pipelines.rest.routes.{BaseRoutesTest, SecureRouteSettings}

class UploadRoutesTest extends BaseRoutesTest {

  "UploadRoutes" should {
    "create and push to a new source" in {

      val service   = PipelineService()(Scheduler(executor))
      val settings  = SecureRouteSettings("index.html", "foo", "bar")
      val undertest = SourceRoutes(service, settings)

      //
      import io.circe.literal._
      Post("/source/push/new", json"""{ "hi" : "there" }""") ~> undertest.routes ~> check {
        println(responseText)
      }
    }

  }

  "UploadRoutes.upload" should {
    "upload files" in {

      ???
      val service   = PipelineService()(Scheduler(executor))
      val settings  = SecureRouteSettings("index.html", "foo", "bar")
      val undertest = SourceRoutes(service, settings)

      //
      import io.circe.literal._
      Post("/source/push/new", json"""{ "hi" : "there" }""") ~> undertest.routes ~> check {
        println(responseText)
      }
    }

  }
}
