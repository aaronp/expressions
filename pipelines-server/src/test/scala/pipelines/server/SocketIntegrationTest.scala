package pipelines.server

import args4c.implicits._
import pipelines.client.jvm.{ClientSocketStateJVM, PipelinesClient}
import pipelines.mongo.StartMongo
import pipelines.rest.socket.handlers.SubscriptionHandler
import pipelines.rest.socket.{AddressedMessageRouter, ClientSocket, SocketConnectionAck}
import pipelines.rest.{RestSettings, RunningServer}
import pipelines.server.PipelinesMain.{Bootstrap, defaultTransforms}
import pipelines.ssl.CertSetup
import pipelines.{DevRestMain, Env, Using}

import scala.concurrent.Future

class SocketIntegrationTest extends BaseServiceSpec {


  class TestHandler extends SubscriptionHandler {

  }
  override type ServiceType = String
  override def startServer(): RunningServer[ServiceType] = {
    StartMongo.main(Array.empty)
    val originalConfig = PipelinesMainDev.devArgs.asConfig()
    val rootConfig             = CertSetup.ensureCerts(originalConfig)
    val settings: RestSettings = RestSettings(rootConfig)

    val bootstrap = new Bootstrap(settings, defaultTransforms)
    val routes = bootstrap.routes()
    RunningServer.start(settings, bootstrap.sslConf, "bootstrap.commandRouter", )
  }

  "SocketIntegration" should {
    "send AddressedMessage records over websockets" in {
      Using(Env()) { implicit clientEnv =>
        val config = DevRestMain.devArgs.asConfig().resolve()
        val session: ClientSocketStateJVM = eventually {
          val client: PipelinesClient[Future] = PipelinesClient(config)(clientEnv.ioScheduler).get
          client.newSession("admin", "password").futureValue
        }

        val wsClient: ClientSocket = session.socket

        val ackReply = wsClient.expect[SocketConnectionAck](1)
        session.requestHandshake()

        implicit val sched = clientEnv.computeScheduler

        val List(handshake) = ackReply.futureValue
        handshake.commonId should not be null

        wsClient.send(123)
        wsClient.send("Hello")
        wsClient.send(true)

        val router: AddressedMessageRouter = server.service
      }
    }
  }
}
