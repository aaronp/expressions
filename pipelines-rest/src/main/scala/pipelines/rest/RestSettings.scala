package pipelines.rest

import akka.http.scaladsl.server.Route
import args4c.obscurePassword
import com.typesafe.config.Config
import pipelines.Env
import pipelines.reactive.PipelineService
import pipelines.reactive.rest.{SourceRoutes, TransformRoutes}
import pipelines.rest.routes.{SecureRouteSettings, StaticFileRoutes, WebSocketTokenCache}
import pipelines.rest.socket.handlers.SubscriptionHandler
import pipelines.rest.socket.{AddressedMessageRouter, SocketRoutes}
import pipelines.users.LoginHandler
import pipelines.users.rest.UserLoginRoutes

import scala.compat.Platform
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

case class RestSettings(rootConfig: Config, host: String, port: Int, env: Env) {

  def loginRoutes(loginHandler: LoginHandler[Future] = LoginHandler(rootConfig))(implicit ec: ExecutionContext): UserLoginRoutes = {
    UserLoginRoutes(
      secret = rootConfig.getString("pipelines.www.jwtSeed"),
      realm = Option(rootConfig.getString("pipelines.www.realmName")).filterNot(_.isEmpty)
    )(loginHandler.login)
  }

  val secureSettings: SecureRouteSettings = SecureRouteSettings.fromRoot(rootConfig)

  val staticRoutes: StaticFileRoutes = StaticFileRoutes(rootConfig.getConfig("pipelines.www"), secureSettings)

  val tokenValidityDuration: FiniteDuration = {
    import args4c.implicits._
    rootConfig.asFiniteDuration("pipelines.rest.socket.tokenValidityDuration")
  }

  def repoRoutes(socketHandler: SubscriptionHandler, pipelineService: PipelineService, commandRouter: AddressedMessageRouter): Route = {
    import akka.http.scaladsl.server.Directives._
    val srcRoutes  = SourceRoutes(pipelineService, secureSettings).routes
    val transRoute = TransformRoutes(pipelineService, secureSettings).routes
    srcRoutes ~ transRoute ~ createSocketRoute(socketHandler, pipelineService, commandRouter).routes
  }

  def createSocketRoute(socketHandler: SubscriptionHandler, service: PipelineService, commandRouter: AddressedMessageRouter): SocketRoutes = {
    // create a temp look-up from a string which can be used as the websocket protocol to the user's JWT
    val tokens = WebSocketTokenCache(tokenValidityDuration)(env.ioScheduler)
    new SocketRoutes(secureSettings, tokens, socketHandler, service, commandRouter)
  }

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

object RestSettings {

  def apply(rootConfig: Config, env: Env = pipelines.Env()): RestSettings = {
    new RestSettings(rootConfig, host = rootConfig.getString("pipelines.rest.host"), port = rootConfig.getInt("pipelines.rest.port"), env)
  }
}
