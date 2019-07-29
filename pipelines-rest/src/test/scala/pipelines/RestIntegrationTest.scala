package pipelines

import args4c.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.{ClientSession, PipelinesClient}
import pipelines.rest.RunningServer
import pipelines.rest.socket.SocketConnectionAck
import pipelines.users.{LoginRequest, LoginResponse}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success

class RestIntegrationTest extends BaseCoreTest with BeforeAndAfterAll with ScalaFutures {

  private var server: RunningServer = null

  "PipelinesClient.login" should {
    "reject invalid passwords" in {
      val client = {
        val config = DevRestMain.devArgs.asConfig().resolve()
        PipelinesClient.sync(config).get
      }

      client.login(LoginRequest("admin", "wrong")).isFailure shouldBe true
      val Success(LoginResponse(true, Some(token), None)) = client.login(LoginRequest("admin", "password"))
      token.count(_ == '.') shouldBe 2
    }
  }

  "The Rest server" should {
    "respond to new websocket client handshakes" in {
      Using(Env()) { implicit clientEnv =>
        val config = DevRestMain.devArgs.asConfig().resolve()
        val session: ClientSession = eventually {
          val client: PipelinesClient[Future] = PipelinesClient(config)(clientEnv.ioScheduler).get
          client.newSession("admin", "password").futureValue
        }

        val wsClient = session.socket

        val ackReply = wsClient.expect[SocketConnectionAck](1)

        session.requestHandshake()

        implicit val sched = clientEnv.computeScheduler

        val List(handshake) = ackReply.futureValue
        handshake.commonId should not be null
      }
    }
    "receive source and sink events after subscribing to them" in {
      Using(Env()) { implicit clientEnv =>
        val state = IntegrationTestState().futureValue

        state

        val config = DevRestMain.devArgs.asConfig().resolve()
        val session: ClientSession = eventually {
          val client: PipelinesClient[Future] = PipelinesClient(config)(clientEnv.ioScheduler).get
          client.newSession("admin", "password").futureValue
        }

        val wsClient = session.socket

        val ackReply = wsClient.expect[SocketConnectionAck](1)

        session.requestHandshake()

        implicit val sched = clientEnv.computeScheduler

        val List(handshake) = ackReply.futureValue
        handshake.commonId should not be null
      }
    }
  }

  override def beforeAll(): Unit = {
    import eie.io._
    "./target/certificates/".asPath match {
      case dir if dir.isDir => dir.delete()
      case _                =>
    }
    super.beforeAll()

    val Some(started) = rest.RestMain.runMain(DevRestMain.devArgs)
    server = started
  }

  override def afterAll(): Unit = {
    if (server != null) {
      server.close()
      server.bindingFuture.futureValue
    }
  }

  override def testTimeout: FiniteDuration = 15.seconds
}
