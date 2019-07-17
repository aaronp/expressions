package pipelines

import args4c.implicits._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.{ClientSession, PipelinesClient}
import pipelines.rest.RunningServer
import pipelines.rest.socket.{AddressedMessage, AddressedTextMessage}
import pipelines.users.{CreateUserRequest, CreateUserResponse, LoginRequest, LoginResponse}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

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

    "handle client websocket requests" in {

      Using(Env()) { implicit clientEnv =>
        val config = DevRestMain.devArgs.asConfig().resolve()
        val session: ClientSession[Future] = eventually {
          val client = PipelinesClient(config)(clientEnv.ioScheduler).get
          client.newSession("admin", "password").futureValue
        }
        val wsClient = session.socket

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
