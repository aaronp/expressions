package pipelines.server

import args4c.ConfigApp
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import pipelines.reactive._
import pipelines.rest.RestMain.ensureCerts
import pipelines.rest.routes.TraceRoute
import pipelines.rest.socket.AddressedMessage
import pipelines.rest.{RestMain, RunningServer, Settings}
import pipelines.ssl.SSLConfig
import pipelines.users.LoginHandler
import pipelines.users.mongo.LoginHandlerMongo

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

  def transforms(): Map[String, Transform] = {

    Transform
      .defaultTransforms()
      .updated("TraceRoute.httpRequestTransform", TraceRoute.httpRequestTransform)
      .updated("UploadEvent.asAddressedMessage", Transform.map[UploadEvent, AddressedMessage](UploadEvent.asAddressedMessage))
  }

  def run(rootConfig: Config): Future[RunningServer] = {
    logger.info(RestMain.startupLog(rootConfig, getClass))
    val config             = ensureCerts(rootConfig)
    val settings: Settings = Settings(config)
    val service            = PipelineService(transforms())(settings.env.ioScheduler)

    implicit val ioExecCtxt = settings.env.ioScheduler
    val loginHandlerFuture: Future[LoginHandler[Future]] = LoginHandler.handlerClassName(rootConfig) match {
      case c1ass if c1ass.isAssignableFrom(classOf[LoginHandlerMongo]) =>
        LoginHandlerMongo(rootConfig)
      case _ => Future.successful(LoginHandler(rootConfig))
    }

    loginHandlerFuture.map { loginHandler =>
      val sslConf: SSLConfig = SSLConfig(settings.rootConfig)
      val route              = PipelineServerRoutes(sslConf, settings, service, loginHandler)

      PipelineService.registerOwnSourcesAndSink(service)

      RunningServer(settings, sslConf, route)
    }
  }

}
