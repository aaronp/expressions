package pipelines.rest.openapi

import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.circe._
import io.circe.parser._
import org.scalatest.{Matchers, WordSpec}

class DocumentationRoutesTest extends WordSpec with Matchers with ScalatestRouteTest {

  "DocumentationRoutes.route" should {
    // this is broken w/ circe schemas -- a conflict in the imports
    "serve docs" in {

      Get("/openapi.json") ~> DocumentationRoutes.route ~> check {
        val docs        = responseAs[String]
        val Right(json) = decode[Json](docs)

        val paths = json.hcursor.downField("paths")

        val Some(keys) = paths.keys.map(_.toList.sorted)
        val cs         = Documentation.documentedEndpoints.map(_.path).distinct.sorted

        keys should not be (empty)
        val desc: String = keys
          .zip(cs)
          .map {
            case (a, b) => s"\t${a.padTo(20, ' ')} ==> $b"
          }
          .mkString("\n")

        withClue(s"${json.spaces4}\nwhich is:\n$desc\n\n") {
          keys should contain theSameElementsAs (cs)
        }
      }
    }
  }
}
