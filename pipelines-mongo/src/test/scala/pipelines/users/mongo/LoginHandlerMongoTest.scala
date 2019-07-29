package pipelines.users.mongo

import pipelines.{Schedulers, WithScheduler}
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings}
import pipelines.users.jvm.UserHash
import pipelines.users.{CreateUserRequest, LoginRequest}

import scala.concurrent.duration._

trait LoginHandlerMongoTest extends BasePipelinesMongoSpec {

  "LoginHandlerMongo" should {
    "be able to log in a newly created user" in {
      Schedulers.using { implicit sched =>
        val authService: UserRolesService = {
          val usersCollectionName = s"users-${System.currentTimeMillis}"
          val rolesCollectionName = s"roles-${System.currentTimeMillis}"

          val rolesSettings = CollectionSettings(configForCollection(rolesCollectionName, basedOn = "roles"), rolesCollectionName)
          val userSettings  = CollectionSettings(configForCollection(usersCollectionName, basedOn = "userRoles"), usersCollectionName)

          UserRolesService(userSettings, rolesSettings).futureValue
        }

        val users = {
          val usersCollectionName = s"users-${System.currentTimeMillis}"
          val config              = configForCollection(usersCollectionName)
          val settings            = CollectionSettings(config, usersCollectionName)
          UserRepoMongo(settings, UserHash(config)).futureValue
        }

        try {

          val underTest      = new LoginHandlerMongo(users, authService, 10.minutes)
          val userEnteredPwd = "password"
          users.createUser(CreateUserRequest("under", "t@st.com", userEnteredPwd)).futureValue

          val Some(byUserName) = underTest.login(LoginRequest("under", userEnteredPwd)).futureValue
          byUserName.email shouldBe "t@st.com"

          val Some(byEmailName) = underTest.login(LoginRequest("t@st.com", userEnteredPwd)).futureValue
          byEmailName.email shouldBe "t@st.com"

          byUserName.name shouldBe byEmailName.name

          underTest.login(LoginRequest("t@st.com", "wrong")).futureValue shouldBe None

        } finally {
          authService.authCollection.drop()
          authService.userRolesCollection.drop()
        }
      }

    }
  }
}
