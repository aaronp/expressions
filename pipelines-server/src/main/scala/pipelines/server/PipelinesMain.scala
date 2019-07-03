package pipelines.server

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.{Route, RouteResult}
import args4c.ConfigApp
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import pipelines.mongo.users.{LoginHandlerMongo, UserServiceMongo}
import pipelines.reactive.{DataSource, PipelineService}
import pipelines.rest.RestMain.ensureCerts
import pipelines.rest.RunningServer.reduce
import pipelines.rest.openapi.DocumentationRoutes
import pipelines.rest.routes.TraceRoute
import pipelines.rest.users.{UserRoleRoutes, UserRoutes}
import pipelines.rest.{RestMain, RunningServer, Settings}
import pipelines.socket.SocketRoutesSettings
import pipelines.ssl.SSLConfig
import pipelines.users.LoginHandler

import scala.concurrent.Future

/**
  * It's often the case that the 'web' (REST) becomes the dumping-ground for a massive "super-do-it" app.
  *
  * I wanted to ensure that wasn't the case by not letting 'pipelines-rest' depend on e.g. mongo, kafka, etc.
  *
  * That way we can ensure that we can add new routes and just compose that into our existing REST service.
  *
  * It also should help us eventually extract the 'users' stuff into a separate 'users' project that just deals
  * w/ users, login, roles, etc.
  */
object PipelinesMain extends ConfigApp with StrictLogging {
  type Result = Future[RunningServer]

  override def defaultConfig(): Config = ConfigFactory.load()

  override protected val configKeyForRequiredEntries = "pipelines.requiredConfig"

  def run(rootConfig: Config) = {
    logger.info(RestMain.startupLog(rootConfig, getClass))
    val config             = ensureCerts(rootConfig)
    val settings: Settings = Settings(config)
    val service            = PipelineService()(settings.env.ioScheduler)

    implicit val ioExecCtxt = settings.env.ioScheduler
    val loginHandlerFuture: Future[LoginHandler[Future]] = LoginHandler.handlerClassName(rootConfig) match {
      case c1ass if c1ass.isAssignableFrom(classOf[LoginHandlerMongo]) =>
        LoginHandlerMongo(rootConfig)
      case _ => Future.successful(LoginHandler(rootConfig))
    }

    loginHandlerFuture.map { loginHandler =>
      start(settings, service, loginHandler)
    }
  }

  def start(settings: Settings, service: PipelineService, loginHandler: LoginHandler[Future]): RunningServer = {
    val sslConf: SSLConfig = SSLConfig(settings.rootConfig)
    val socketSettings     = SocketRoutesSettings(settings.rootConfig, settings.secureSettings, settings.env)

    val pipelinesService = PipelineService()(settings.env.ioScheduler)

    val route: Route = {
      val login = settings.loginRoutes(sslConf, loginHandler).routes
      val additionalRoutes: Seq[Route] = {
        loginHandler match {
          case mongo: LoginHandlerMongo =>
            val userService: UserServiceMongo = new UserServiceMongo(mongo)
            implicit val ec                   = settings.env.ioScheduler
            val userRoutes                    = UserRoutes(userService, socketSettings.secureSettings)
            val userRoleRoutes                = UserRoleRoutes(userService, socketSettings.secureSettings)
            val repoRoutes                    = settings.repoRoutes(pipelinesService)
            Seq(repoRoutes, userRoutes.routes, userRoleRoutes.routes)
          case _ => Nil
        }
      }

      import settings.env._
      val repoRoutes: Seq[Route] = {
        settings.staticRoutes.route +:
          DocumentationRoutes.route +:
          login +:
          socketSettings.routes +:
          additionalRoutes
      }

      def addSrc[A](src: DataSource.PushSource[A]) = {
        val Seq(created: DataSource.PushSource[A]) = pipelinesService.getOrCreateSource(src)
        pipelinesService.getOrCreateSource(created).ensuring(_.head == created, "getOrCreate should've returned the same source")
        created
      }
      val usersRequests: DataSource.PushSource[HttpRequest] = {
        addSrc(
          DataSource
            .createPush[HttpRequest]
            .apply(settings.env.ioScheduler) //
            .addMetadata("label", "http-requests"))
      }
      val usersResponses: DataSource.PushSource[(HttpRequest, Long, RouteResult)] = {
        addSrc(
          DataSource
            .createPush[(HttpRequest, Long, RouteResult)]
            .apply(settings.env.ioScheduler) //
            .addMetadata("label", "http-request-response"))
      }
      val trace = TraceRoute
        .onRequest { req: HttpRequest =>
          try {
            TraceRoute.log.onRequest(req)
            usersRequests.push(req)
          } catch {
            case e: Throwable =>
              logger.error("monika : " + e, e)
          }
        }
        .onResponse {
          case tuple @ (req, duration, resp) =>
            try {
              TraceRoute.log.onResponse(req, duration, resp)
              usersResponses.push(tuple)
            } catch {
              case e: Throwable =>
                logger.error("bang : " + e, e)
            }
        }

      Route.seal(trace.wrap(reduce(repoRoutes)))
    }

    RunningServer(settings, sslConf, route)
  }
}
