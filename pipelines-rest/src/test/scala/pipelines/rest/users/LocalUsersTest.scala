package pipelines.rest.users

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import pipelines.users.{LocalUsers, LoginHandler, LoginRequest}
import pipelines.{BaseCoreTest, WithTempDir}

class LocalUsersTest extends BaseCoreTest with ScalaFutures {

  "LocalUsers" should {
    "be able to handle logins based on users on the file system" in {
      WithTempDir { dir =>
        val userDir = dir.toAbsolutePath.resolve("users")
        val config  = ConfigFactory.parseString(s"""
          |pipelines.users.loginHandler = ${classOf[LocalUsers].getName}
          |pipelines.users.sessionDuration=5s
          |pipelines.users.local.createIfNotExists = true
          |pipelines.users.local.dir = ${userDir}
        """.stripMargin)

        import eie.io._
        withClue("our user dir should not have been created yet") {
          userDir.exists() shouldBe false
        }

        val handler = LoginHandler(config).asInstanceOf[LocalUsers]

        // create a couple users
        handler.userNameDir.resolve(LocalUsers.safe("someAdmin")).resolve("password").text = "special"
        handler.emailDir.resolve(LocalUsers.safe("text@example.com")).resolve("password").text = "letMeIn"

        handler.login(LoginRequest("meh", "")).futureValue shouldBe None
        handler.login(LoginRequest("someAdmin", "")).futureValue shouldBe None
        handler.login(LoginRequest("someAdmin", "password")).futureValue shouldBe None

        val Some(adminClaims) = handler.login(LoginRequest("someAdmin", "special")).futureValue
        adminClaims.name shouldBe "someAdmin"
      }
    }
  }
}
