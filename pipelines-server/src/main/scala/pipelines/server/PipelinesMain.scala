package pipelines.server

import args4c.ConfigApp
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import pipelines.mongo.MongoConnect
import pipelines.reactive._
import pipelines.rest.routes.TraceRoute
import pipelines.rest.socket.{AddressedMessage, AddressedMessageRouter}
import pipelines.rest.{RestMain, RunningServer, Settings}
import pipelines.ssl.{CertSetup, SSLConfig}
import pipelines.users.LoginHandler
import pipelines.users.mongo.LoginHandlerMongo

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

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
  type Result = RunningServer[AddressedMessageRouter]

  override def defaultConfig(): Config = ConfigFactory.load()

  override protected val configKeyForRequiredEntries = RestMain.configKeyForRequiredEntries

  def transforms(): Map[String, Transform] = {

    Transform
      .defaultTransforms()
      .updated("TraceRoute.httpRequestTransform", TraceRoute.httpRequestTransform)
      .updated("UploadEvent.asAddressedMessage", Transform.map[UploadEvent, AddressedMessage](UploadEvent.asAddressedMessage))
  }

  def run(originalConfig: Config): Result = {
    logger.info(RestMain.startupLog(originalConfig, getClass))
    val rootConfig         = CertSetup.ensureCerts(originalConfig)
    val settings: Settings = Settings(rootConfig)
    val service            = PipelineService(transforms())(settings.env.ioScheduler)

    val mongoDb = {
      val conn = MongoConnect(rootConfig)
      val c    = conn.client
      c.getDatabase(conn.database)
    }
    val commandRouter = AddressedMessageRouter(service)

    implicit val ioExecCtxt = settings.env.ioScheduler
    val loginHandlerFuture: Future[LoginHandler[Future]] = LoginHandler.handlerClassName(rootConfig) match {
      case c1ass if c1ass.isAssignableFrom(classOf[LoginHandlerMongo]) =>
        LoginHandlerMongo(mongoDb, rootConfig)
      case _ =>
        Future.successful(LoginHandler(rootConfig))
    }

    // let's block here and throw in the main start-up thread if anything's amiss
    val loginHandler = Await.result(loginHandlerFuture, Duration.Inf)
    logger.info(s"Starting with $loginHandler")

    val sslConf: SSLConfig = SSLConfig(rootConfig)

    // init the handlers
    val handlers = RestMain.DefaultHandlers(commandRouter)

    val route = PipelineServerRoutes(sslConf, settings, handlers.subscriptionHandler, loginHandler)

    RunningServer(settings, sslConf, commandRouter, route)
  }
}
