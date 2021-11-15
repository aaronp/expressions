package expressions.rest.server

import cats.data.OptionT
import cats.effect.{Sync, *}
import com.typesafe.config.Config
import eie.io.*
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Request, Response, StaticFile}

import java.nio.file.{Paths, Path as JPath}
import scala.concurrent.ExecutionContext

/**
  * TODO:
  *
  * $ consider zipping the static resources on disk, and so just serve them up already zipped to client
  * $ ...otherwise zip the results and set the expiry time
  *
  * @param htmlRootDirs the directory to otherwise serve up html resources
  * @param landingPage  the redirect landing page (e.g. index.html)
  * @param jsRootDirs   the directory which will serve the /js artifacts
  * @param cssRootDirs  the directory which will serve the /css artifacts
  */
case class StaticFileRoutes(htmlRootDirs: Seq[JPath],
                            hostPort: String,
                            landingPage: String,
                            jsRootDirs: Seq[JPath],
                            assetsDirs: Seq[JPath],
                            cssRootDirs: Seq[JPath],
                            resourceMap: Map[String, Option[String]]) {
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  htmlRootDirs.foreach { htmlRootDir =>
    require(htmlRootDir.exists(), s"htmlRootDir '$htmlRootDir' doesn't exist (working dir is ${(Paths.get(".").toAbsolutePath)})")
  }

  cssRootDirs.foreach { cssRootDir => //: JPath
    require(cssRootDir.exists(), s"cssRootDir '$cssRootDir' doesn't exist (${cssRootDir.toAbsolutePath})")
  }

  def routes[F[_]: Async](): HttpRoutes[F] = {
    val builder = new Builder[F]()
    builder.routes
  }

  private class Builder[F[_]: Async]() {
    private val dsl = Http4sDsl[F]

    import dsl._

    def routes = {
      HttpRoutes.of[F] {
        case request @ GET -> Root / "js" / path                                                 => getJS(path, request)
        case request @ GET -> Root / "css" / path                                                => getCSS(path, request)
        case request@GET -> Root / "assets" / "fonts" / path => getAsset(s"fonts/$path", request)
        case request@GET -> Root / "assets" / "AssetManifest.json" => getAsset("AssetManifest.json", request)
        case request@GET -> Root / "assets" / "FontManifest.json" => getAsset("FontManifest.json", request)
        case request@GET -> Root / "assets" / "MaterialIcons-Regular.otf" => getAsset("FontManifest.json", request)
        case request@GET -> Root / "assets" / path => getAsset(s"assets/$path", request)
        case request @ GET -> Root / "assets" / "packages" / "cupertino_icons" / "assets" / path => getHTML(s"assets/packages/cupertino_icons/assets/$path", request)
        case request @ GET -> Root / "assets" / "fonts" / path                                   => getHTML(s"assets/fonts/$path", request)
        case request @ GET -> Root / "assets" / path                                             => getHTML(s"assets/$path", request)
        case request @ GET -> Root / "icons" / path                                              => getHTML(s"icons/$path", request)
        case request @ GET -> Root / "#" / path                                                              =>
          println(s"Ignoring $path, returning $landingPage")
          getHTML(landingPage, request)
        case request @ GET -> Root                                                               => getHTML(landingPage, request)
        case request @ GET -> Root / path                                                        => getHTML(path, request)
      }
    }

    private def getCSS(unmatchedPath: String, request: Request[F]): F[Response[F]] = {
      resolvePaths(cssRootDirs, unmatchedPath, request).getOrElseF(NotFound())
    }

    private def getAsset(unmatchedPath: String, request: Request[F]): F[Response[F]] = {
      logger.trace(s"getAsset('$unmatchedPath') under asset dirs ${assetsDirs}")
      resolvePaths(assetsDirs, unmatchedPath, request).getOrElseF(NotFound())
    }

    private def getHTML(unmatchedPath: String, request: Request[F]): F[Response[F]] = {
      resolvePaths(htmlRootDirs, unmatchedPath, request).getOrElseF(NotFound())
    }

    private def getJS(unmatchedPath: String, request: Request[F]): F[Response[F]] = {
      val key: String = unmatchedPath.toString
      logger.trace(s"Serving '$key' under JS dirs ${jsRootDirs}")
      val opt: OptionT[F, Response[F]] = resourceMap.get(key) match {
        case Some(Some(differentName)) =>
          logger.trace(s"Mapping $unmatchedPath to '${differentName}' under JS dir ${jsRootDirs}")
          resolvePaths(jsRootDirs, differentName, request)
        case Some(None) =>
          logger.trace(s"Rejecting $key")
          OptionT.none[F, Response[F]]
        case None =>
          resolvePaths(jsRootDirs, unmatchedPath, request)
      }

      opt.getOrElseF(NotFound())
    }

    private def resolvePaths(paths: Seq[JPath], resource: String, request: Request[F]): OptionT[F, Response[F]] = {
      val opts = paths.map { path =>
        resolvePath(path, resource, request)
      }
      val optT = opts.reduce(_ orElse _)
      resource match {
        case "main.dart.js" => optT.map { resp =>
          val originalContent: fs2.Stream[F, String] = resp.bodyText

          val content = originalContent.map { body =>
            StaticFileRoutes.fixForOffline(body, hostPort)
          }

          resp.withEntity(content)
        }
        case _ => optT
      }
    }

    private def resolvePath(path: JPath, resource: String, request: Request[F]): OptionT[F, Response[F]] = {
      val resolved = path.resolve(resource)
      logger.trace(s"Fetching '$resolved'")
      StaticFile.fromFile(resolved.toFile, Some(request))
    }
  }

}

object StaticFileRoutes {

//  private lazy val staticBlocker: Blocker = Blocker.liftExecutionContext(ExecutionContext.Implicits.global)

  def localhost = java.net.InetAddress.getLocalHost.getHostName

  /**
    * dart uses a lot of CDNs for ... stuff.
    *
    * To use offline, it's easier to just 'sed' the main.dart.js js code, which contains lines like:
    * {{{
    *for(f=J.aG(c);f.q();)h.push(m.of(a0.BN(J.av(f.gE(f),"asset")),d))}if(!g)h.push(m.of("https://fonts.gstatic.com/s/roboto/v20/KFOmCnqEu92Fr1Me5WZLCzYlKw.ttf","Roboto"))
    * }}}
    *
    * and
    * {{{
    *   $2(a,b){return C.b.O("http://localhost:8080/",a)},
    * }}}
    *
    * the 'http://localhost:8080/' reference comes from our buld.sh, which has:
    * {{{
    *   --dart-define=FLUTTER_WEB_CANVASKIT_URL=CANVASKIT_REPLACEME
    * }}}
    *
    * so ... we'll swap 'CANVASKIT_REPLACEME' with our own hostname as taken from the request URI, and the 'https://fonts.gstatic.com/s/roboto/v20/' w/ the same host uri (assuming we can serve up the KFOmCnqEu92Fr1Me5WZLCzYlKw.ttf asset locally)
    *
    * @param body
    * @param hostName
    * @return
    */
  def fixForOffline(body: String, hostName: String) = {
    val fixed = body.replace("https://fonts.gstatic.com/s/roboto/v20/KFOmCnqEu92Fr1Me5WZLCzYlKw.ttf", s"${hostName}/assets/KFOmCnqEu92Fr1Me5WZLCzYlKw.ttf")
      .replace("CANVASKIT_REPLACEME", s"$hostName/")
      .replace("http://localhost:8080", hostName)
    fixed
  }

  /** @param rootConfig the top-level config, e.g. the result of calling 'ConfigFactory.load()'
    * @return the StaticFileRoutes
    */
  def apply(appConfig: Config): StaticFileRoutes = {
    fromWWWConfig(appConfig.getString("hostPort"), appConfig.getConfig("www"))
  }

  /**
    * @param wwwConfig the relative config which contains the static file route entries
    * @return the StaticFileRoutes
    */
  def fromWWWConfig(hostPort: String, wwwConfig: Config): StaticFileRoutes = {
    import args4c.implicits._

    val resourceMapping: Map[String, Option[String]] = wwwConfig.getConfig("resourceMapping").collectAsMap().map {
      case (key, value) =>
        val unquoted = args4c.unquote(key) match {
          case k if k.startsWith("/") => k.tail
          case k                      => k
        }
        unquoted -> Option(args4c.unquote(value.trim)).filterNot(_.isEmpty)
    }

    wwwConfig.getString("extractTo") match {
      case ""  =>
      case dir => ExtractJar.extractResourcesFromJar(dir.asPath)
    }

    def dirs(key: String) = wwwConfig.asList(key).map(_.trim).map(p => Paths.get(p))

    new StaticFileRoutes(
      htmlRootDirs = dirs("htmlDir"),
      hostPort = hostPort,
      landingPage = wwwConfig.getString("landingPage"),
      jsRootDirs = dirs("jsDir"),
      assetsDirs = dirs("assetsDir"),
      cssRootDirs = dirs("cssDir"),
      resourceMap = resourceMapping
    )
  }
}
