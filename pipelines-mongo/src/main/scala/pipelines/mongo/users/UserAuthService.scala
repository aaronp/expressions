package pipelines.mongo.users

import com.typesafe.config.Config
import monix.execution.{CancelableFuture, Scheduler}
import pipelines.mongo.CollectionSettings
import pipelines.users.{AuthModel, UserRoles}

/**
  * Contains the info for logging in/updating users, as well as their roles
  */
case class UserAuthService(authRepo: RefDataMongo[AuthModel], users: RefDataMongo[UserRoles]) {
  private[users] def authCollection = authRepo.repo.collection
  private[users] def userCollection = users.repo.collection

  private val nowt = Set.empty[String]
  def permissionsForRole(roleId: String): Set[String] = {
    authRepo.latestModel().fold(nowt) { model =>
      model.permissionsByRole.getOrElse(roleId, nowt)
    }
  }

  def rolesForUser(userId: String): Set[String] = users.latestModel().fold(nowt)(_.rolesByUserId.getOrElse(userId, nowt))

  def permissionsForUser(userId: String): Set[String] = {
    val all = for {
      authModel <- authRepo.latestModel()
      roles     = rolesForUser(userId)
    } yield {
      roles.flatMap(authModel.permissionsByRole.getOrElse(_, nowt))
    }
    all.getOrElse(nowt)
  }
}

object UserAuthService {
  def apply(rootConfig: Config)(implicit ioScheduler: Scheduler): CancelableFuture[UserAuthService] = {
    val userRoles: CollectionSettings = CollectionSettings(rootConfig, "userRoles")
    val roles                         = CollectionSettings(rootConfig, "roles")
    apply(userRoles, roles)
  }
  def apply(users: CollectionSettings, roles: CollectionSettings)(implicit ioScheduler: Scheduler): CancelableFuture[UserAuthService] = {
    val second = RefDataMongo[UserRoles](users)
    for {
      authService <- RefDataMongo[AuthModel](roles)
      userService <- second
    } yield {
      new UserAuthService(authService, userService)
    }
  }
}
