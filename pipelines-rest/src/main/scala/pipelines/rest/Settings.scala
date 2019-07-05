package pipelines.rest

import akka.http.scaladsl.server.Route
import args4c.obscurePassword
import com.typesafe.config.Config
import pipelines.Env
import pipelines.reactive.PipelineService
import pipelines.reactive.rest.SourceRoutes
import pipelines.rest.routes.{SecureRouteSettings, StaticFileRoutes}
import pipelines.ssl.SSLConfig
import pipelines.users.LoginHandler
import pipelines.users.rest.UserLoginRoutes

import scala.compat.Platform
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

case class Settings(rootConfig: Config, host: String, port: Int, env: Env) {

  def loginRoutes(sslConf: SSLConfig, loginHandler: LoginHandler[Future] = LoginHandler(rootConfig)): UserLoginRoutes = {
    UserLoginRoutes(
      secret = rootConfig.getString("pipelines.www.jwtSeed"),
      realm = Option(rootConfig.getString("pipelines.www.realmName")).filterNot(_.isEmpty)
    )(loginHandler.login)(ExecutionContext.global)
  }

  val secureSettings: SecureRouteSettings = SecureRouteSettings.fromRoot(rootConfig)

  val staticRoutes: StaticFileRoutes = StaticFileRoutes(rootConfig.getConfig("pipelines.www"), secureSettings)

  val tokenValidityDuration: FiniteDuration = {
    import args4c.implicits._
    rootConfig.asFiniteDuration("pipelines.rest.socket.tokenValidityDuration")
  }
  def repoRoutes(service: PipelineService): Route = SourceRoutes(service, secureSettings).routes

  override def toString: String = {
    import args4c.implicits._
    def obscure: (String, String) => String = obscurePassword(_, _)
    val indentedConfig = rootConfig
      .getConfig("pipelines")
      .summaryEntries(obscure)
      .map { e =>
        s"\tpipelines.$e"
      }
      .mkString(Platform.EOL)
    s"""$host:$port
       |pipelines config:
       |${indentedConfig}
     """.stripMargin
  }
}

object Settings {

  def apply(rootConfig: Config): Settings = {
    val config = rootConfig.getConfig("pipelines")
    val env    = pipelines.Env()
    new Settings(rootConfig, host = config.getString("rest.host"), port = config.getInt("rest.port"), env)
  }
}
