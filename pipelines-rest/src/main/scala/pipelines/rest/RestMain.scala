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
  type Result = RunningServer[(AddressedMessageRouter, PipelineService)]

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
  case class DefaultHandlers(commandRouter: AddressedMessageRouter, pipelinesService: PipelineService) {
    val subscriptionHandler = SubscriptionHandler.register(commandRouter, pipelinesService)
    PipelineService.registerOwnSourcesAndSink(pipelinesService)
  }

  def run(rootConfig: Config): Result = {
    val config                   = CertSetup.ensureCerts(rootConfig)
    val settings                 = RestSettings(config)
    val service: PipelineService = PipelineService()(settings.env.ioScheduler)

    logger.warn(s"Starting with\n${settings}\n\n")

    run(settings, service)
  }

  def run(settings: RestSettings, service: PipelineService): Result = {
    val commandRouter                      = AddressedMessageRouter()
    val sslConf: SSLConfig                 = SSLConfig(settings.rootConfig)
    val loginHandler: LoginHandler[Future] = LoginHandler(settings.rootConfig)

    val login    = settings.loginRoutes(loginHandler)(settings.env.ioScheduler).routes
    val handlers = DefaultHandlers(commandRouter, service)

    val repoRoutes = settings.repoRoutes(handlers.subscriptionHandler, service, commandRouter)

    val route: Route = {
      import settings.env._
      RunningServer.makeRoutes(Seq(login, repoRoutes))
    }
    RunningServer.start(settings, sslConf, commandRouter -> service, route)
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
