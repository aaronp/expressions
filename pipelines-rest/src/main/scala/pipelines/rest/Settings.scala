package pipelines.rest

import akka.http.scaladsl.server.Route
import args4c.obscurePassword
import com.typesafe.config.Config
import pipelines.Env
import pipelines.reactive.PipelineService
import pipelines.reactive.rest.{SourceRoutes, TransformRoutes}
import pipelines.rest.routes.{SecureRouteSettings, StaticFileRoutes, WebSocketTokenCache}
import pipelines.rest.socket.{ServerSocket, SocketRoutes}
import pipelines.ssl.SSLConfig
import pipelines.users.rest.UserLoginRoutes
import pipelines.users.{Claims, LoginHandler}

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

  def repoRoutes(service: PipelineService): Route = {
    import akka.http.scaladsl.server.Directives._
    val srcRoutes  = SourceRoutes(service, secureSettings).routes
    val transRoute = TransformRoutes(service, secureSettings).routes
    srcRoutes ~ transRoute ~ createSocketRoute(service).routes
  }

  def createSocketRoute(pipelinesService: PipelineService): SocketRoutes = {

    /**
      * What to do when a new WebSocket is opened? Register a source and sink!
      */
    def handleSocket(user: Claims, socket: ServerSocket, queryMetadata: Map[String, String]): Unit = {
      socket.register(user, queryMetadata, pipelinesService)
    }

    // create a temp look-up from a string which can be used as the websocket protocol to the user's JWT
    val tokens = WebSocketTokenCache(tokenValidityDuration)(env.ioScheduler)
    new SocketRoutes(secureSettings, tokens, handleSocket)
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

object Settings {

  def apply(rootConfig: Config): Settings = {
    val config = rootConfig.getConfig("pipelines")
    val env    = pipelines.Env()
    new Settings(rootConfig, host = config.getString("rest.host"), port = config.getInt("rest.port"), env)
  }
}
