package pipelines.server

import akka.http.scaladsl.server.Route
import pipelines.reactive.PipelineService
import pipelines.rest.RestSettings
import pipelines.rest.RunningServer.reduce
import pipelines.rest.openapi.DocumentationRoutes
import pipelines.rest.socket.handlers.SubscriptionHandler
import pipelines.ssl.SSLConfig
import pipelines.users.LoginHandler
import pipelines.users.mongo.{LoginHandlerMongo, UserServiceMongo}
import pipelines.users.rest.{UserRoleRoutes, UserRoutes}

import scala.concurrent.Future

object PipelineServerRoutes {

  def apply(sslConf: SSLConfig, settings: RestSettings, socketHandler: SubscriptionHandler, pipelineService: PipelineService, loginHandler: LoginHandler[Future]): Route = {

    val additionalRoutes: Seq[Route] = {
      loginHandler match {
        case mongo: LoginHandlerMongo =>
          val userService: UserServiceMongo = new UserServiceMongo(mongo)
          implicit val ec                   = settings.env.ioScheduler
          val userRoutes                    = UserRoutes(userService, settings.secureSettings)
          val userRoleRoutes                = UserRoleRoutes(userService, settings.secureSettings)
          val commandHandler                = AddressedMessageRouter()
          val repoRoutes                    = settings.repoRoutes(socketHandler, pipelineService, commandHandler)
          Seq(repoRoutes, userRoutes.routes, userRoleRoutes.routes)
        case _ =>
          // TODO - expose routes for e.g. local NIO handlers, or different databases
          Nil
      }
    }

    val login = settings.loginRoutes(loginHandler)(settings.env.ioScheduler).routes

    import settings.env._
    val repoRoutes: Seq[Route] = {
      settings.staticRoutes.route +:
        DocumentationRoutes.route +:
        login +:
        additionalRoutes
    }

    val trace = RouteTraceAsSource(pipelineService)(settings.env.ioScheduler)
    Route.seal(trace.wrap(reduce(repoRoutes)))
  }
}
