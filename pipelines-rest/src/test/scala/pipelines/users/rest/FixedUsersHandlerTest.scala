package pipelines.users.rest

import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import pipelines.users.{FixedUsersHandler, LoginHandler, LoginRequest}

class FixedUsersHandlerTest extends WordSpec with Matchers with ScalaFutures {
  "FixedUsersHandler.apply(users...)" should {
    "expose a handler for the given users" in {
      val handler = FixedUsersHandler("foo" -> "bar", "x" -> "y")
      handler.login(LoginRequest("foo", "bar")).futureValue.isDefined shouldBe true
      handler.login(LoginRequest("foo", "wrong")).futureValue.isDefined shouldBe false
      handler.login(LoginRequest("x", "y")).futureValue.isDefined shouldBe true
      handler.login(LoginRequest("wrong", "y")).futureValue.isDefined shouldBe false
    }
  }
  "FixedUsersHandler" should {
    "work w/ users set in a configuration" in {
      val handler = LoginHandler(ConfigFactory.parseString(s"""
          |pipelines.users.sessionDuration : 10s
          |pipelines.users.loginHandler=${classOf[FixedUsersHandler].getName}
          |pipelines.users.fixed : {
          |  bob : bobsPassword
          |  felix : foo
          |  admin : hi
          |}
        """.stripMargin))

      handler.login(LoginRequest("bob", "bobsPassword")).futureValue.isDefined shouldBe true
      handler.login(LoginRequest("bob", "invalid")).futureValue.isDefined shouldBe false
    }
  }
}
