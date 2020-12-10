package expressions.rest

import cats.effect.ConcurrentEffect
import com.typesafe.config.{Config, ConfigFactory}
import expressions.rest.RestApp.Settings
import expressions.rest.server.{RestRoutes, StaticFileRoutes}
import org.http4s.HttpRoutes
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
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
    val httpApp = org.http4s.server
      .Router[Task](
        "/"     -> StaticFileRoutes(riffConfig).routes[Task](),
        "/rest" -> restRoutes
      )
      .orNotFound
    if (logHeaders || logBody) {
      Logger.httpApp(logHeaders, logBody)(httpApp)
    } else httpApp
  }

  @implicitNotFound("You need ConcurrentEffect, which (if you're calling w/ a ZIO runtime in scope), can be fixed by: import zio.interop.catz._")
  def serve(implicit ce: ConcurrentEffect[Task]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    for {
      env        <- ZIO.environment[ZEnv]
      httpRoutes = mkRouter(RestRoutes(env))
      exitCode <- BlazeServerBuilder[Task](ExecutionContext.global)
        .bindHttp(port, host)
        .withHttpApp(httpRoutes)
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
        .fold(_ => ExitCode.failure, _ => ExitCode.success)
    } yield exitCode
  }
}

object RestApp {

  def apply(rootConfig: Config = ConfigFactory.load()): RestApp = apply(Settings(rootConfig.getConfig("app")))

  def apply(settings: Settings): RestApp = new RestApp(settings)

  case class Settings(riffConfig: Config, host: String, port: Int, logHeaders: Boolean, logBody: Boolean)

  object Settings {
    def apply(config: Config): Settings = {
      Settings(
        config,
        host = config.getString("host"),
        port = config.getInt("port"),
        logHeaders = config.getBoolean("logHeaders"),
        logBody = config.getBoolean("logBody")
      )
    }

    def fromRootConfig(rootConfig: Config = ConfigFactory.load()): Settings = Settings(rootConfig.getConfig("app"))
  }
}
