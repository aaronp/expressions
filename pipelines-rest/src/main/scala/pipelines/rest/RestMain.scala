package pipelines.rest

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Route
import args4c.ConfigApp
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import pipelines.reactive.{PipelineService, tags}
import pipelines.rest.socket.AddressedMessageRouter
import pipelines.rest.socket.handlers.SubscriptionHandler
import pipelines.ssl.{CertSetup, SSLConfig}
import pipelines.users.{Claims, LoginHandler}

import scala.concurrent.Future

/**
  * The main entry point for the REST service
  *
  * (If you change/rename this, be sure to update pipelines-deploy/src/main/resources/boot.sh and project/Build.scala)
  *
  */
object RestMain extends ConfigApp with StrictLogging {
  type Result = RunningServer[AddressedMessageRouter]

  override val configKeyForRequiredEntries = "pipelines.rest.requiredConfig"

  def queryParamsForUri(uri: Uri, claims: Claims): Map[String, String] = {
    uri.query().toMap.mapValues {
      case null => ""
      case text =>
        val withId = if (claims.userId != null) {
          text.replaceAllLiterally(tags.UserId, claims.userId)
        } else {
          text
        }
        withId.replaceAllLiterally(tags.UserName, claims.name)
    }
  }

  /**
    * Register some common socket handlers
    *
    * @param commandRouter
    * @return
    */
  case class DefaultHandlers(commandRouter: AddressedMessageRouter) {
    val subscriptionHandler = SubscriptionHandler.register(commandRouter)
    PipelineService.registerOwnSourcesAndSink(commandRouter.pipelinesService)
  }

  def run(rootConfig: Config): Result = {
    val config   = CertSetup.ensureCerts(rootConfig)
    val settings = Settings(config)
    val service  = PipelineService()(settings.env.ioScheduler)

    val commandRouter: AddressedMessageRouter = AddressedMessageRouter(service)

    logger.warn(s"Starting with\n${settings}\n\n")

    run(settings, commandRouter)
  }

  def run(settings: Settings, commandRouter: AddressedMessageRouter): Result = {
    val sslConf: SSLConfig                 = SSLConfig(settings.rootConfig)
    val loginHandler: LoginHandler[Future] = LoginHandler(settings.rootConfig)

    val login    = settings.loginRoutes(loginHandler)(settings.env.ioScheduler).routes
    val handlers = DefaultHandlers(commandRouter)

    val repoRoutes = settings.repoRoutes(handlers.subscriptionHandler)

    val route: Route = {
      import settings.env._
      RunningServer.makeRoutes(Seq(login, repoRoutes))
    }
    RunningServer(settings, sslConf, commandRouter, route)
  }

  /** @param config the config used to start this app
    * @param c1ass  the main class running
    * @return a pretty-print of the config keys/values (minus sensitive ones) of our wejo config and from where those values were loaded
    */
  def startupLog(config: Config, c1ass: Class[_] = getClass): String = {
    val appName = c1ass.getSimpleName.filter(_.isLetter)

    val pipelinesConf = config
      .getConfig("pipelines")
      .summary()
      .lines
      .map { line =>
        s"\tpipelines.${line}"
      }
      .mkString("\n")
    s"Running ${appName} with: \n${pipelinesConf}\n\n"
  }
}
