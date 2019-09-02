package pipelines.server

import args4c.implicits._
import com.typesafe.config.ConfigFactory
import pipelines.client.jvm.PipelinesClient
import pipelines.layout.AsciiTable
import pipelines.mongo.StartMongo
import pipelines.reactive._
import pipelines.reactive.repo.ListSinkResponse
import pipelines.rest.socket.{AddressedMessage, ClientSocket, SocketConnectionAck}
import pipelines.rest.{RestSettings, RunningServer}
import pipelines.server.PipelinesMain.{Bootstrap, defaultTransforms}
import pipelines.ssl.CertSetup
import pipelines.{Env, Using}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import cats.instances.future._

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
        handshake.user.name shouldBe userName
      }
    }
    "send AddressedMessage records from a client to the server over websockets" in {
      Using(Env()) { implicit clientEnv =>
        implicit val execContext = clientEnv.ioScheduler

        When("We watch for new sources/sinks on the service")
        val pipelineService    = runningServer.serverData.pipelineService
        val serverSourceEvents = ListBuffer[SourceEvent]()
        pipelineService.sources.events.foreach { srcEvent =>
          serverSourceEvents += srcEvent
        }

        val serverSinkEvents = ListBuffer[SinkEvent]()
        pipelineService.sinks.events.foreach { sinkEvent =>
          serverSinkEvents += sinkEvent
        }

        Given("A client connected to our server for some new user")
        val client: PipelinesClient[Future] = newAsyncClient(clientEnv.ioScheduler).get

        val userName = createNewUser(client).futureValue
        serverSinkEvents.clear()
        serverSourceEvents.clear()
        val session = client.newSession(userName, defaultPassword).futureValue
        val serverSideSource: DataSource = eventually {
          val List(OnSourceAdded(newSource, _)) = serverSourceEvents.toList
          newSource
        }
        val serverSideSink: DataSink = eventually {
          val List(OnSinkAdded(newSink, _)) = serverSinkEvents.toList
          newSink
        }

        When("We observe the data stream on the server")
        withClue("The socket source should always return the same multi-cast observable ") {
          (serverSideSource.asObservable[AddressedMessage] eq serverSideSource.asObservable[AddressedMessage]) shouldBe true
        }

        // let's observe twice - just to ensure we don't end up using 'publishToOne' semantics and breaking subsequent observers
        // of the data
        val receivedOnTheServer1 = ListBuffer[AddressedMessage]()
        serverSideSource.asObservable[AddressedMessage].foreach(receivedOnTheServer1 += _)
        val receivedOnTheServer2 = ListBuffer[AddressedMessage]()
        serverSideSource.asObservable[AddressedMessage].foreach(receivedOnTheServer2 += _)

        val wsClient: ClientSocket = session.socket

        When("The client sends some data")
        wsClient.send(123).futureValue
        wsClient.send("Hello").futureValue
        wsClient.send(true).futureValue

        Then("We should see that data arrive on the server via its socket source")
        val expectedFirstMessages = List(AddressedMessage(123), AddressedMessage("Hello"), AddressedMessage(true))
        receivedOnTheServer1 should contain theSameElementsAs(expectedFirstMessages)
        receivedOnTheServer2 should contain theSameElementsAs(expectedFirstMessages)
      }
    }
    "send AddressedMessage records to clients from the server over a websocket" in {
      Using(Env()) { implicit clientEnv =>
        implicit val execContext = clientEnv.ioScheduler

        When("We watch for new sources/sinks on the service")
        val pipelineService    = runningServer.serverData.pipelineService
        val serverSourceEvents = ListBuffer[SourceEvent]()
        pipelineService.sources.events.foreach { srcEvent =>
          serverSourceEvents += srcEvent
        }

        val serverSinkEvents = ListBuffer[SinkEvent]()
        pipelineService.sinks.events.foreach { sinkEvent =>
          serverSinkEvents += sinkEvent
        }

        Given("A client connected to our server for some new user")
        val client: PipelinesClient[Future] = newAsyncClient(clientEnv.ioScheduler).get


      }
    }
  }
}
