package pipelines.server

import java.util.UUID

import args4c.implicits._
import cats.Functor
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.PipelinesClient
import pipelines.rest.RunningServer
import pipelines.users.CreateUserRequest
import pipelines.{BaseCoreTest, DevRestMain}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

abstract class BaseServiceSpec extends BaseCoreTest with BeforeAndAfterAll with ScalaFutures with StrictLogging {

  type ServiceType
  protected var runningServer: RunningServer[ServiceType] = null

  def defaultPassword = "correct password"
  def newClient(): PipelinesClient[Try] = {
    val config = DevRestMain.devArgs.asConfig().resolve()
    PipelinesClient.sync(config).get
  }

  def newAsyncClient(implicit ec: ExecutionContext): Try[PipelinesClient[Future]] = {
    val config = DevRestMain.devArgs.asConfig().resolve()
    PipelinesClient(config)(ec)
  }

  def createNewUser[F[_]: Functor](client: PipelinesClient[F], userName: String = s"${getClass} user ${UUID.randomUUID()}".filter(_.isLetterOrDigit)): F[String] = {
    val email = userName + "@email.com"

    val responseF = client.newUser(CreateUserRequest(userName, email, defaultPassword))
    implicitly[Functor[F]].map(responseF) { createResponse =>
      createResponse.ok shouldBe true
      createResponse.jwtToken.isDefined shouldBe true
      createResponse.error shouldBe empty
      userName
    }
  }

  def startServer(): RunningServer[ServiceType]

  override def beforeAll(): Unit = {
    super.beforeAll()
    import eie.io._
    "./target/certificates/".asPath match {
      case dir if dir.isDir => dir.delete()
      case _                =>
    }

    runningServer = startServer()
  }
  override def afterAll(): Unit = {
    if (runningServer != null) {
      runningServer.close()
      runningServer.bindingFuture.futureValue
    }
  }

  override def testTimeout: FiniteDuration = 1.minute
}
