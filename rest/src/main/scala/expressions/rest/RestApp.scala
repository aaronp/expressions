package expressions.rest

import cats.effect.ConcurrentEffect
import com.typesafe.config.{Config, ConfigFactory}
import expressions.rest.RestApp.Settings
import expressions.rest.server.record.LiveRecorder
import expressions.rest.server.{RestRoutes, StaticFileRoutes}
import org.http4s.HttpRoutes
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.middleware.{CORS, Logger}
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

/**
  * @param settings
  */
case class RestApp(settings: Settings) {

  import settings._

  private def mkRouter(restRoutes: HttpRoutes[Task]) = {
    val logAction = if (recordRequestResponse) {
      Option(LiveRecorder.recordSession())
    } else {
      None
    }

    val httpApp = Router[Task](
      "/"     -> StaticFileRoutes(riffConfig).routes[Task](), //
      "/rest" -> CORS(restRoutes) //
    ).orNotFound

    if (logHeaders || logBody || recordRequestResponse) {
      Logger.httpApp(logHeaders, logBody, logAction = logAction)(httpApp)
    } else httpApp
  }

  @implicitNotFound("You need ConcurrentEffect, which (if you're calling w/ a ZIO runtime in scope), can be fixed by: import zio.interop.catz._")
  def serve(rootConfig: Config)(implicit ce: ConcurrentEffect[Task]): ZIO[zio.ZEnv, Nothing, ExitCode] =
    for {
      rawRoutes  <- RestRoutes(rootConfig).orDie
      httpRoutes = mkRouter(rawRoutes)
      exitCode <- BlazeServerBuilder[Task](ExecutionContext.global)
        .bindHttp(port, host)
        .withHttpApp(httpRoutes)
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
        .exitCode
    } yield exitCode
}

object RestApp {

  def apply(rootConfig: Config = ConfigFactory.load()): RestApp = apply(Settings(rootConfig.getConfig("app")))

  def apply(settings: Settings): RestApp = new RestApp(settings)

  case class Settings(riffConfig: Config, host: String, port: Int, logHeaders: Boolean, logBody: Boolean, recordRequestResponse: Boolean)

  object Settings {
    def apply(config: Config): Settings = {
      Settings(
        config,
        host = config.getString("host"),
        port = config.getInt("port"),
        logHeaders = config.getBoolean("logHeaders"),
        logBody = config.getBoolean("logBody"),
        recordRequestResponse = config.getBoolean("recordRequestResponse")
      )
    }

    def fromRootConfig(rootConfig: Config = ConfigFactory.load()): Settings = Settings(rootConfig.getConfig("app"))
  }
}
