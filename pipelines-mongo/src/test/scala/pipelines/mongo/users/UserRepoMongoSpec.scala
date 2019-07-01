package pipelines.mongo.users

import pipelines.WithScheduler
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings}
import pipelines.users.CreateUserRequest
import pipelines.users.jvm.UserHash

trait UserRepoMongoSpec extends BasePipelinesMongoSpec {

  "UserServiceMongo.find" should {
    "find users by email or password" in {
      WithScheduler { implicit s =>
        val usersCollectionName = s"users-${System.currentTimeMillis}"
        val config              = configForCollection(usersCollectionName)
        val settings            = CollectionSettings(config, usersCollectionName)
        val userService         = UserRepoMongo(settings, UserHash(config)).futureValue

        try {

          Given("A new user 'dave'")
          val daveReq = CreateUserRequest("dave", s"d@ve.com", "password")
          // this should succeed:
          userService.createUser(daveReq).futureValue

          val Some(foundByName)  = userService.findUser("dave").futureValue
          val Some(foundByEmail) = userService.findUser("d@ve.com").futureValue
          foundByName shouldBe foundByEmail
          foundByEmail.id.length should be > 5
          foundByName.id shouldBe foundByEmail.id
          foundByName.id should not be (empty)
        } finally {
          userService.users.drop().monix.completedL.runToFuture.futureValue
        }
      }
    }
  }
  "UserServiceMongo.createUser" should {
    "not be able to create a user w/ the same name or email" in {
      WithScheduler { implicit s =>
        val usersCollectionName = s"users-${System.currentTimeMillis}"
        val config              = configForCollection(usersCollectionName)
        val settings            = CollectionSettings(config, usersCollectionName)
        val userService         = UserRepoMongo(settings, UserHash(config)).futureValue

        try {

          Given("A new user 'dave'")
          val daveReq = CreateUserRequest("dave", s"d@ve.com", "password")
          // this should succeed:
          userService.createUser(daveReq).futureValue

          When("We try to create him a second time")
          Then("It should fail")

          val Left(error) = userService.createUser(daveReq).futureValue
          error.message shouldBe "That user already exists"

          And("It should fail if just the email is different")
          val Left(bang2) = userService.createUser(daveReq.copy(email = "changed" + daveReq.email)).futureValue
          bang2.message shouldBe "That user already exists"

          And("It should fail if just the name is different")
          val Left(bang3) = userService.createUser(daveReq.copy(userName = "changed" + daveReq.userName)).futureValue
          bang3.message shouldBe "That user already exists"

        } finally {
          userService.users.drop().monix.completedL.runToFuture.futureValue
        }
      }
    }
  }
}
