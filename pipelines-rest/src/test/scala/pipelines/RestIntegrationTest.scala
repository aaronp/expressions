package pipelines

import args4c.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.PipelinesClient
import pipelines.rest.RunningServer
import pipelines.socket.{AddressedMessage, AddressedTextMessage, SocketSettings}
import pipelines.users.{LoginRequest, LoginResponse}

import scala.concurrent.duration._
import scala.util.Success

class RestIntegrationTest extends BaseCoreTest with BeforeAndAfterAll with ScalaFutures {

  private var server: RunningServer = null

  "PipelinesClient.login" should {
    "reject invalid passwords" in {
      val client = {
        val config = DevMain.devArgs.asConfig().resolve()
        PipelinesClient(config).get
      }

      client.login(LoginRequest("admin", "wrong")).isFailure shouldBe true
      val Success(LoginResponse(true, Some(token), None)) = client.login(LoginRequest("admin", "password"))
      token.count(_ == '.') shouldBe 2
    }
  }
  "The Rest server" should {
    "handle client websocket requests" in {

      val session = eventually {
        val Success(client) = {
          val config = DevMain.devArgs.asConfig().resolve()
          PipelinesClient(config)
        }
        val Success(connected) = client.newSession("admin", "password")
        connected
      }
      Using(Env()) { clientEnv =>
        val wsClient = session.socket(SocketSettings(getClass.getSimpleName))(clientEnv).futureValue

        val echoReply = wsClient.toClientOutput
          .dump("toClientOutput")
          .collect {
            case AddressedTextMessage("hello", echo) if echo.startsWith("echo:") => echo
          }
          .take(1)
          .runAsyncGetFirst(clientEnv.ioScheduler)

        wsClient.toServerInput.onNext(AddressedMessage("hello", "world"))

        implicit val sched = clientEnv.computeScheduler

        echoReply.futureValue shouldBe Some("echo: world")
      }
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    val Some(started) = rest.Main.runMain(DevMain.devArgs)
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
