package pipelines

import java.util.UUID

import args4c.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.{ClientSocketStateJVM, PipelinesClient}
import pipelines.reactive.repo.{CreatedPushSourceResponse, PushSourceResponse}
import pipelines.reactive.{ContentType, PushEvent}
import pipelines.rest.RunningServer
import pipelines.rest.socket.{AddressedMessage, AddressedMessageRouter, SocketConnectionAck}
import pipelines.users.{CreateUserRequest, CreateUserResponse, LoginRequest, LoginResponse}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

class RestIntegrationTest extends BaseCoreTest with BeforeAndAfterAll with ScalaFutures with StrictLogging {

  private var server: RunningServer[AddressedMessageRouter] = null

  def newClient() = {

    val config = DevRestMain.devArgs.asConfig().resolve()
    PipelinesClient.sync(config).get

  }
  "PipelinesClient.login" should {

    "accept valid logins" in {
      val client: PipelinesClient[Try] = newClient()

      val userName = createNewUser(client)

      val Success(LoginResponse(true, tokenOpt, Some(user), None)) = client.login(LoginRequest(userName, "correct password"))
      tokenOpt.get.count(_ == '.') shouldBe 2
      user.name shouldBe userName
    }

    "reject invalid logins" in {
      val client: PipelinesClient[Try] = newClient()
      client.login(LoginRequest("admin", "wrong")) shouldBe Success(LoginResponse.failed)

      val userName                                        = createNewUser(client)
      val Success(LoginResponse(false, None, None, None)) = client.login(LoginRequest(userName, "wrong password"))
    }
  }

  "The Rest server" should {
    "respond to new websocket client handshakes" ignore {
      Using(Env()) { implicit clientEnv =>
        val config = DevRestMain.devArgs.asConfig().resolve()
        val session: ClientSocketStateJVM = eventually {
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
        val client: PipelinesClient[Try] = newClient()
        val userName                     = createNewUser(client)

        val state = IntegrationTestState(userName, "correct password").futureValue

        implicit val sched = clientEnv.ioScheduler
        val received       = ListBuffer[AddressedMessage]()
        state.session.messages2.foreach { fromServer =>
          println(s"\tXXXX fromServer2 -> $fromServer")
//          received += fromServer
        //          fromServer
        }
        state.session.messages.foreach { fromServer =>
          println(s"\tXXXX fromServer -> $fromServer")
          received += fromServer
        }

        withClue("Initially there should be one trigger which listens for new sources/sinks in order to add handlers") {
          eventually {
            server.service.pipelinesService.triggers.currentState.fold(0)(_.triggers.size) shouldBe 1
          }
        }

        logger.info("""
             |:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?
             |state.session.subscribeToSourceEvents()
             |:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?
          """.stripMargin)
        val subId = state.session.subscribeToSourceEvents().futureValue

//        eventually {
//          server.service.triggers.currentState.fold(0)(_.triggers.size) shouldBe 2
//        }
        logger.info("""
                      |:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?
                      |subscribed....
                      |:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?:!:?
                    """.stripMargin)

        import io.circe.literal._

        val newSourceName                = s"${getClass.getSimpleName}-${UUID.randomUUID.toString}"
        val response: PushSourceResponse = state.session.pushToSource(json"""{ "test" : "value" }""", name = newSourceName).futureValue

        val CreatedPushSourceResponse(`newSourceName`, contentType, metadata) = response
        contentType shouldBe ContentType.of[PushEvent]
        metadata.get(pipelines.reactive.tags.SourceType) shouldBe Some(pipelines.reactive.tags.typeValues.Push)

        eventually {
          received.size should be > 0
        }
        println()
      }
    }
  }

  def createNewUser(client: PipelinesClient[Try], userName: String = s"${getClass} user ${UUID.randomUUID()}".filter(_.isLetterOrDigit)) = {
    val email                                       = userName + "@email.com"
    val Success(createResponse: CreateUserResponse) = client.newUser(CreateUserRequest(userName, email, "correct password"))
    createResponse.ok shouldBe true
    createResponse.jwtToken.isDefined shouldBe true
    createResponse.error shouldBe empty
    userName
  }

  override def beforeAll(): Unit = {
//    "./target/certificates/".asPath match {
//      case dir if dir.isDir => dir.delete()
//      case _                =>
//    }
    super.beforeAll()

//    val Some(started) = rest.RestMain.runMain(DevRestMain.devArgs)
//    server = started
  }

  override def afterAll(): Unit = {
    if (server != null) {
      server.close()
      server.bindingFuture.futureValue
    }
  }

  override def testTimeout: FiniteDuration = 7.minutes
}
