package pipelines.mongo.users

import com.typesafe.config.Config
import monix.execution.{CancelableFuture, Scheduler}
import pipelines.audit.VersionDetails
import pipelines.auth.{SetRolesForUserRequest, UserRoles}
import pipelines.core.{GenericErrorResult, GenericMessageResult}
import pipelines.users.{CreateUserRequest, RegisteredUser, UserService}

import scala.concurrent.{ExecutionContext, Future}

class UserServiceMongo(override val loginHandler: LoginHandlerMongo) extends UserService[Future] {

  private def userRoleRepo: RefDataMongo[UserRoles] = loginHandler.userRoles.userRoleRepo
  override def updateUserRoles(updatingUser: String, request: SetRolesForUserRequest) = {
    implicit def ec: ExecutionContext = userRoleRepo.ioScheduler
    loginHandler.userRoles.updateUserRoles(updatingUser, request)
  }

  override def userRolesOpt(): Future[Option[(VersionDetails, UserRoles)]] = {
    val opt = loginHandler.userRoles.userRoleRepo.latest().map {
      case (vers, model) => (vers.version, model)
    }
    Future.successful(opt)
  }

  override def findUser(usernameOrEmail: String): Future[Option[RegisteredUser]] = {
    loginHandler.users.findUser(usernameOrEmail)
  }

  override def createUser(request: CreateUserRequest): CancelableFuture[Either[GenericErrorResult, GenericMessageResult]] = {
    loginHandler.users.createUser(request)
  }
}

object UserServiceMongo {
  def apply(rootConfig: Config)(implicit ioSched: Scheduler): Future[UserServiceMongo] = {
    LoginHandlerMongo(rootConfig).map { login =>
      new UserServiceMongo(login)
    }
  }
}
