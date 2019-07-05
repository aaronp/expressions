package pipelines.server

import akka.http.scaladsl.server.Route
import pipelines.reactive.PipelineService
import pipelines.rest.RunningServer.reduce
import pipelines.rest.Settings
import pipelines.rest.openapi.DocumentationRoutes
import pipelines.rest.routes.WebSocketTokenCache
import pipelines.rest.socket._
import pipelines.ssl.SSLConfig
import pipelines.users.mongo.{LoginHandlerMongo, UserServiceMongo}
import pipelines.users.rest.{UserRoleRoutes, UserRoutes}
import pipelines.users.{Claims, LoginHandler}

import scala.concurrent.Future

object PipelineServerRoutes {

  def apply(sslConf: SSLConfig, settings: Settings, pipelinesService: PipelineService, loginHandler: LoginHandler[Future]): Route = {

    val additionalRoutes: Seq[Route] = {
      loginHandler match {
        case mongo: LoginHandlerMongo =>
          val userService: UserServiceMongo = new UserServiceMongo(mongo)
          implicit val ec                   = settings.env.ioScheduler
          val userRoutes                    = UserRoutes(userService, settings.secureSettings)
          val userRoleRoutes                = UserRoleRoutes(userService, settings.secureSettings)
          val repoRoutes                    = settings.repoRoutes(pipelinesService)
          Seq(repoRoutes, userRoutes.routes, userRoleRoutes.routes)
        case _ =>
          // TODO - expose routes for e.g. local NIO handlers, or different databases
          Nil
      }
    }

    val socketRoutes: Route = createSocketRoute(settings, pipelinesService).routes

    val login = settings.loginRoutes(sslConf, loginHandler).routes

    import settings.env._
    val repoRoutes: Seq[Route] = {
      settings.staticRoutes.route +:
        DocumentationRoutes.route +:
        login +:
        socketRoutes +:
        additionalRoutes
    }

    val trace = RouteTraceAsSource(pipelinesService)(settings.env.ioScheduler)
    Route.seal(trace.wrap(reduce(repoRoutes)))
  }

  def createSocketRoute(settings: Settings, pipelinesService: PipelineService): SocketRoutes = {

    /**
      * What to do when a new WebSocket is opened? Register a source and sink!
      */
    def handleSocket(user: Claims, socket: ServerSocket, queryMetadata: Map[String, String]): Unit = {
      socket.register(user, queryMetadata, pipelinesService)
    }

    // create a temp look-up from a string which can be used as the websocket protocol to the user's JWT
    val tokens = WebSocketTokenCache(settings.tokenValidityDuration)(settings.env.ioScheduler)
    new SocketRoutes(settings.secureSettings, tokens, handleSocket)
  }
}
