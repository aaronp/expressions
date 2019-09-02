package pipelines.server

import args4c.implicits._
import com.typesafe.config.ConfigFactory
import pipelines.client.jvm.PipelinesClient
import pipelines.layout.AsciiTable
import pipelines.mongo.StartMongo
import pipelines.rest.socket.{ClientSocket, SocketConnectionAck}
import pipelines.rest.{RestSettings, RunningServer}
import pipelines.server.PipelinesMain.{Bootstrap, defaultTransforms}
import pipelines.ssl.CertSetup
import pipelines.{Env, Using}

import scala.concurrent.Future

class SocketIntegrationTest extends BaseServiceSpec {

  override type ServiceType = Bootstrap
  override def startServer(): RunningServer[ServiceType] = {
    StartMongo.main(Array.empty)
    val originalConfig         = PipelinesMainDev.devArgs.asConfig(ConfigFactory.load()).resolve()
    val rootConfig             = CertSetup.ensureCerts(originalConfig)
    val settings: RestSettings = RestSettings(rootConfig)

    val bootstrap = new Bootstrap(settings, defaultTransforms)
    val routes    = bootstrap.routes()
    RunningServer.start(settings, bootstrap.sslConf, bootstrap, routes)
  }

  "WebSocket clients" should {
    "be able to request and receive SocketConnectionAck as a 'handshake'" in {
      Using(Env()) { implicit clientEnv =>
        implicit val execContext = clientEnv.ioScheduler
        import cats.instances.future._

        Given("A client connected to our server for some new user")
        val client: PipelinesClient[Future] = newAsyncClient(clientEnv.ioScheduler).get

        val userName               = createNewUser(client).futureValue
        val session                = client.newSession(userName, defaultPassword).futureValue
        val wsClient: ClientSocket = session.socket

        When("We request a handshake")
        val ackReply = wsClient.expect[SocketConnectionAck](1)
        session.requestHandshake()

        Then("We should receive a SocketConnectionAck containing the source/sink IDs associated with our socket, as well as our user details")
        val List(handshake) = ackReply.futureValue
        handshake.commonId should not be null
        handshake.user.userId shouldBe userName
      }
    }
    "send AddressedMessage records over websockets" in {
      Using(Env()) { implicit clientEnv =>
        implicit val execContext = clientEnv.ioScheduler
        import cats.instances.future._

        Given("A client connected to our server for some new user")
        val client: PipelinesClient[Future] = newAsyncClient(clientEnv.ioScheduler).get

        val userName               = createNewUser(client).futureValue
        val session                = client.newSession(userName, defaultPassword).futureValue
        val wsClient: ClientSocket = session.socket

        When("The client sends some data")
        wsClient.send(123)
        wsClient.send("Hello")
        wsClient.send(true)

        val serverService = runningServer.serverData.pipelineService
        val sources = serverService.listSources(Map.empty)
        println(AsciiTable(sources.sources).render())

        val sinks = serverService.listSinks(Map.empty)
        println(AsciiTable(sinks.sinks).render())
      }
    }
  }
}
