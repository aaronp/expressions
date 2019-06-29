package pipelines.rest

import akka.http.scaladsl.server.Route
import args4c.obscurePassword
import com.typesafe.config.Config
import pipelines.Env
import pipelines.reactive.repo.rest.SourceRepoRoutes
import pipelines.rest.routes.{SecureRouteSettings, StaticFileRoutes}
import pipelines.rest.users.UserRoutes
import pipelines.ssl.SSLConfig
import pipelines.users.LoginHandler

import scala.compat.Platform
import scala.concurrent.ExecutionContext

case class Settings(rootConfig: Config, host: String, port: Int, env: Env) {

  def userRoutes(sslConf: SSLConfig): UserRoutes = {
    val handler = LoginHandler(rootConfig)
    UserRoutes(
      secret = rootConfig.getString("pipelines.www.jwtSeed"),
      realm = Option(rootConfig.getString("pipelines.www.realmName")).filterNot(_.isEmpty)
    )(handler.login)(ExecutionContext.global)
  }

  val secureSettings: SecureRouteSettings = SecureRouteSettings.fromRoot(rootConfig)

  val staticRoutes: StaticFileRoutes = StaticFileRoutes(rootConfig.getConfig("pipelines.www"), secureSettings)

  def repoRoutes: Route = {
    SourceRepoRoutes(secureSettings).routes
  }

  override def toString: String = {
    import args4c.implicits._
    def obscure: (String, String) => String = obscurePassword(_, _)
    val indentedConfig = rootConfig
      .getConfig("pipelines")
      .summaryEntries(obscure)
      .map { e =>
        s"\t$e"
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
    new Settings(rootConfig, host = config.getString("host"), port = config.getInt("port"), env)
  }
}
