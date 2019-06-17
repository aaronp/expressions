package pipelines.mongo.users

import pipelines.WithScheduler
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings}
import pipelines.users.CreateUserRequest
import pipelines.users.jvm.UserHash

trait UserServiceMongoSpec extends BasePipelinesMongoSpec {

  "UserServiceMongo" should {
    "not be able to create a user w/ the same name or email" in {
      WithScheduler { implicit s =>
        val usersCollectionName = s"users-${System.currentTimeMillis}"
        val config              = configForCollection(usersCollectionName)
        val settings            = CollectionSettings(config, usersCollectionName)
        val userService         = UserServiceMongo(settings, UserHash(config)).futureValue

        try {

          Given("A new user 'dave'")
          val daveReq = CreateUserRequest("dave", s"d@ve.com", "password")
          // this should succeed:
          userService.createUser(daveReq).futureValue

          When("We try to create him a second time")
          Then("It should fail")
          val bang1 = intercept[Exception] {
            userService.createUser(daveReq).futureValue
          }
          bang1.getMessage should include("duplicate key error collection")
          bang1.getMessage should include("index: email_1 dup key: { : \"d@ve.com\" }")

          And("It should fail if just the email is different")
          val bang2 = intercept[Exception] {
            userService.createUser(daveReq.copy(email = "changed" + daveReq.email)).futureValue
          }
          bang2.getMessage should include("duplicate key error collection")
          bang2.getMessage should include("index: userName_1 dup key: { : \"dave\" }")

          And("It should fail if just the name is different")
          val bang3 = intercept[Exception] {
            userService.createUser(daveReq.copy(userName = "changed" + daveReq.userName)).futureValue
          }
          bang3.getMessage should include("duplicate key error collection")
          bang3.getMessage should include("index: email_1 dup key: { : \"d@ve.com\" }")

        } finally {
          userService.users.drop().monix.completedL.runToFuture.futureValue
        }
      }
    }
  }
}
