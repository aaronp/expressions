package pipelines.rest.routes

import akka.http.scaladsl.model.HttpHeader
import pipelines.rest.DevConfig

class StaticFileRoutesTest extends BaseRoutesTest {

  "StaticFileRoutes.route" should {
    "redirect to /login.html from /secure/secret.html if a JWT header is not supplied" ignore {
      val route = DevConfig.staticRoutes().route
      Get("/secure/secret.html") ~> route ~> check {
        response.status.intValue shouldBe 307
        val redirectUrl = response.headers.collectFirst {
          case HttpHeader("location", value) => value
        }
        redirectUrl.getOrElse("no location") should endWith("/login.html?redirectTo=/secure/secret.html")
      }
    }

    "redirect calls to  /js/pipelines.js from the root" in {
      import eie.io._
      val expectedJsResource = "./pipelines-client-xhr/target/scala-2.12/pipelines-client-xhr-fastopt.js".asPath

      val route = DevConfig.staticRoutes()
      Get("/js/pipelines.js") ~> route.route ~> check {
        if (expectedJsResource.exists()) {
          status.intValue shouldBe 200
          responseText should include("pipelines.reactive")
        }
      }
    }
    "redirect to /index from the root" in {
      val route = DevConfig.staticRoutes()
      Get("/") ~> route.route ~> check {
        val indexhtml = responseText
        indexhtml should include("<link rel=\"stylesheet\" type=\"text/css\" href=\"/css/pipelines.css\">")
      }
    }
    "redirect to /index from the normal path" in {
      val route = DevConfig.staticRoutes()
      Get() ~> route.route ~> check {
        val indexhtml = responseText
        indexhtml should include("<link rel=\"stylesheet\" type=\"text/css\" href=\"/css/pipelines.css\">")
      }
    }
    "serve css routes" in {
      val route = DevConfig.staticRoutes()
      Get("/css/pipelines.css") ~> route.route ~> check {
        val cssContent = responseText
        cssContent should include("background-color")
      }
    }

    // this fails if we haven't run 'fastOptJS' or 'fullOptJS' on the xhr project
    import eie.io._
    DevConfig.staticRoutes().jsRootDir.asPath.findFirst(8)(_.fileName.matches("pipelines-client-xhr.*opt.*js")).foreach { jsFile =>
      "serve js routes" in {
        val route = DevConfig.staticRoutes()
        Get(s"/js/${jsFile.fileName}") ~> route.route ~> check {
          val javascript = responseText
          javascript should include("function()")
        }
      }
    }
  }
}
