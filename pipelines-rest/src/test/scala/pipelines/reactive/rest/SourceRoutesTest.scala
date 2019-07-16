package pipelines.reactive.rest

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import monix.execution.Scheduler
import pipelines.reactive.PipelineService
import pipelines.reactive.repo.CreatedPushSourceResponse
import pipelines.rest.DevConfig
import pipelines.rest.routes.BaseRoutesTest
import pipelines.users.Claims
import pipelines.users.jwt.RichClaims._
import pipelines.{Env, Using}

import scala.concurrent.duration._
import scala.util.Success

class SourceRoutesTest extends BaseRoutesTest {
  val settings = DevConfig.secureSettings
  val user     = Claims.after(5.minutes).forUser("server").withId("some id")
  val jwt      = user.asToken(settings.secret)

  "SourceRoutes" should {
    "create and push to a new source" in Using(Env()) { env =>
      val service   = PipelineService()(Scheduler(executor))
      val undertest = SourceRoutes(service, settings)

      import io.circe.literal._

      val request = Post("/source/push/new?createIfMissing=true", json"""{ "hi" : "there" }""").withHeaders(Authorization(OAuth2BearerToken(jwt)))

      request ~> undertest.routes ~> check {
        val Success(created) = io.circe.parser.decode[CreatedPushSourceResponse](responseText).toTry
        created.name.get shouldBe "new"
        created.sourceName shouldBe "new"
        created.id should not be empty
      }
    }
  }
}
