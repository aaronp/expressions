package pipelines.mongo.users

import pipelines.WithScheduler
import pipelines.audit.AuditVersion
import pipelines.auth.{AuthModel, UserRoles}
import pipelines.mongo.{BasePipelinesMongoSpec, CollectionSettings}

trait UserRolesServiceSpec extends BasePipelinesMongoSpec {

  "UsersMongo" should {
    "be able to associate users with permissions" in {
      WithScheduler { implicit sched =>
        val userService: UserRolesService = {
          val usersCollectionName = s"users-${System.currentTimeMillis}"
          val rolesCollectionName = s"roles-${System.currentTimeMillis}"

          val rolesSettings = CollectionSettings(configForCollection(rolesCollectionName, basedOn = "roles"), rolesCollectionName)
          val userSettings  = CollectionSettings(configForCollection(usersCollectionName, basedOn = "userRoles"), usersCollectionName)

          UserRolesService(userSettings, rolesSettings).futureValue
        }

        try {
          Given("some initial roles")
          val baseModel = AuthModel(
            Map(
              "adminRole" -> Set("add users", "delete users"),
              "guestRole" -> Set("view own pages")
            ))

          userService.authRepo
            .updateWith("admin user") {
              case None      => baseModel
              case Some(old) => old.copy(permissionsByRole = old.permissionsByRole ++ baseModel.permissionsByRole)
            }
            .futureValue

          And("some users")
          val baseUsers = UserRoles(
            Map(
              "admin dave"    -> Set("adminRole"),
              "new user carl" -> Set("guestRole")
            )
          )
          userService.userRoleRepo
            .updateWith("another user") {
              case None      => baseUsers
              case Some(old) => old.copy(rolesByUserId = old.rolesByUserId ++ baseUsers.rolesByUserId)
            }
            .futureValue

          Then("The users should have the relevant permissions")
          eventually {
            userService.permissionsForUser("admin dave") should contain only ("add users", "delete users")
            userService.permissionsForUser("new user carl") should contain only ("view own pages")
            userService.permissionsForUser("anonymous") shouldBe (empty)
          }
          userService.permissionsForUser("") shouldBe (empty)

          When("The roles are updated")

          userService.authRepo
            .updateWith("new admin user") {
              case Some(old) =>
                old.copy(
                  permissionsByRole = old.permissionsByRole
                    .updated("adminRole", Set("delete the database"))
                    .updated("newRole", Set("foo")))
            }
            .futureValue

          Then("The user permissions should be affected")
          eventually {
            userService.permissionsForUser("admin dave") should contain only ("delete the database")
            userService.permissionsForUser("new user carl") should contain only ("view own pages")
          }

          When("The users are updated")
          userService.userRoleRepo
            .updateWith("yet another user") {
              case Some(old) => old.copy(rolesByUserId = old.rolesByUserId.updated("admin dave", Set("guestRole", "newRole")))
            }
            .futureValue

          Then("The user permissions should be affected")
          eventually {
            userService.permissionsForUser("admin dave") should contain only ("view own pages", "foo")
            userService.permissionsForUser("new user carl") should contain only ("view own pages")
          }

          val authChanges: Seq[AuditVersion] = userService.authRepo.repo.find().toListL.runSyncUnsafe(testTimeout)
          val userChanges: Seq[AuditVersion] = userService.userRoleRepo.repo.find().toListL.runSyncUnsafe(testTimeout)

          authChanges.map(x => (x.userId, x.revision)).toList shouldBe List("admin user"   -> 1, "new admin user"   -> 2)
          userChanges.map(x => (x.userId, x.revision)).toList shouldBe List("another user" -> 1, "yet another user" -> 2)

        } finally {
          userService.authCollection.drop()
          userService.userRolesCollection.drop()
        }
      }
    }
  }
}
