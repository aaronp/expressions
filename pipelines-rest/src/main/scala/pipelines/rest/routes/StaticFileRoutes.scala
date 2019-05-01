package pipelines.rest.routes

import akka.http.scaladsl.model.{StatusCodes, Uri}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._

import akka.http.scaladsl.server.directives.BasicDirectives.extractUnmatchedPath
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

object StaticFileRoutes {

  /** @param rootConfig the top-level config, e.g. the result of calling 'ConfigFactory.load()'
    * @return the StaticFileRoutes
    */
  def fromRootConfig(rootConfig: Config): StaticFileRoutes = {
    val secureSettings = SecureRouteSettings.fromRoot(rootConfig)
    apply(rootConfig.getConfig("pipelines.www"), secureSettings)
  }

  /**
    * @param wwwConfig the relative config which contains the static file route entries
    * @return the StaticFileRoutes
    */
  def apply(wwwConfig: Config, secureSettings: SecureRouteSettings): StaticFileRoutes = {
    import args4c.implicits._

    val resourceMapping: Map[String, Option[String]] = wwwConfig.getConfig("resourceMapping").collectAsMap().map {
      case (key, value) =>
        args4c.unquote(key) -> Option(args4c.unquote(value.trim)).filterNot(_.isEmpty)
    }

    new StaticFileRoutes(
      htmlRootDir = wwwConfig.getString("htmlDir"),
      landingPage = wwwConfig.getString("landingPage"),
      jsRootDir = wwwConfig.getString("jsDir"),
      cssRootDir = wwwConfig.getString("cssDir"),
      secureSettings = secureSettings,
      secureDirs = wwwConfig.asList("securePaths"),
      resourceMap = resourceMapping
    )
  }
}

/**
  * TODO -
  *
  * $ consider zipping the static resources on disk, and so just serve them up already zipped to client
  * $ ...otherwise zip the results and set the expiry time
  *
  * @param htmlRootDir the directory to otherwise serve up html resources
  * @param landingPage the redirect landing page (e.g. index.html)
  * @param jsRootDir   the directory which will serve the /js artifacts
  * @param cssRootDir  the directory which will serve the /css artifacts
  */
case class StaticFileRoutes(htmlRootDir: String,
                            landingPage: String,
                            jsRootDir: String,
                            cssRootDir: String,
                            secureSettings: SecureRouteSettings,
                            secureDirs: Seq[String],
                            resourceMap: Map[String, Option[String]])
    extends StrictLogging {
  require(!htmlRootDir.endsWith("/"), s"htmlRootDir '$htmlRootDir' shouldn't end w/ a forward slash")
  require(!jsRootDir.endsWith("/"), s"jsRootDir '$jsRootDir' shouldn't end w/ a forward slash")
  require(!cssRootDir.endsWith("/"), s"cssRootDir '$cssRootDir' shouldn't end w/ a forward slash")

  def route: Route = {
    jsResource ~ cssResource ~ htmlResource
  }

  def htmlResource = {
    get {
      (pathEndOrSingleSlash & redirectToTrailingSlashIfMissing(StatusCodes.TemporaryRedirect)) {
        getFromFile(htmlRootDir + landingPage)
      } ~ {
        getFromDirectory(htmlRootDir)
      }
    }
  }

  private def jsResource: Route = {
    (get & pathPrefix("js")) {
      extractUnmatchedPath { unmatchedPath: Uri.Path =>
        val key: String = unmatchedPath.toString
        logger.trace(s"Serving '$key' under JS dir ${jsRootDir}")
        val opt = resourceMap.get(key)
        opt match {
          case Some(Some(differentName)) =>
            logger.trace(s"Mapping $unmatchedPath to '${differentName}' under JS dir ${jsRootDir}")
            getFromFile(jsRootDir + differentName)
          case Some(None) => reject
          case None       => getFromDirectory(jsRootDir)
        }
      }
    }
  }

  private def cssResource = {
    (get & pathPrefix("css")) {
      extractUnmatchedPath { unmatchedPath =>
        logger.trace(s"Serving $unmatchedPath under CSS dir ${cssRootDir}")
        getFromDirectory(cssRootDir)
      }
    }
  }

}
