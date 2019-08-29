package pipelines.server

import java.util.UUID

import args4c.implicits._
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.PipelinesClient
import pipelines.rest.RunningServer
import pipelines.rest.socket.AddressedMessageRouter
import pipelines.users.{CreateUserRequest, CreateUserResponse}
import pipelines.{BaseCoreTest, DevRestMain}

import scala.concurrent.duration._
import scala.util.{Success, Try}

abstract class BaseServiceSpec extends BaseCoreTest with BeforeAndAfterAll with ScalaFutures with StrictLogging {

  type ServiceType
  protected var server: RunningServer[ServiceType] = null

  def newClient(): PipelinesClient[Try] = {
    val config = DevRestMain.devArgs.asConfig().resolve()
    PipelinesClient.sync(config).get
  }

  def createNewUser(client: PipelinesClient[Try], userName: String = s"${getClass} user ${UUID.randomUUID()}".filter(_.isLetterOrDigit)) = {
    val email                                       = userName + "@email.com"
    val Success(createResponse: CreateUserResponse) = client.newUser(CreateUserRequest(userName, email, "correct password"))
    createResponse.ok shouldBe true
    createResponse.jwtToken.isDefined shouldBe true
    createResponse.error shouldBe empty
    userName
  }

  def startServer() : RunningServer[ServiceType]

  override def beforeAll(): Unit = {
    super.beforeAll()
    import eie.io._
    "./target/certificates/".asPath match {
      case dir if dir.isDir => dir.delete()
      case _                =>
    }

    server = startServer()
  }
  override def afterAll(): Unit = {
    if (server != null) {
      server.close()
      server.bindingFuture.futureValue
    }
  }

  override def testTimeout: FiniteDuration = 1.minute
}
