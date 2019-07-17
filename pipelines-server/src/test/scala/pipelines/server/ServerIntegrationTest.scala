package pipelines.server

import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import pipelines.client.jvm.PipelinesClient
import pipelines.{BaseCoreTest, DevRestMain, rest}
import pipelines.rest.RunningServer
import pipelines.users.{CreateUserRequest, CreateUserResponse}

import scala.concurrent.duration._
import scala.util.{Success, Try}

class ServerIntegrationTest extends BaseCoreTest with BeforeAndAfterAll with ScalaFutures {

  import args4c.implicits._
  private var server: RunningServer = null

  "PipelinesClient.newUser" should {
    "be able to create new users" in {
      val client: PipelinesClient[Try] = {
        val config = DevRestMain.devArgs.asConfig().resolve()
        PipelinesClient.sync(config).get
      }
      val response                                             = client.newUser(CreateUserRequest("test-user", "t@st.com", "password"))
      val Success(CreateUserResponse(true, Some(token), None)) = response

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
