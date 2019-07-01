package pipelines.users

import pipelines.audit.VersionDetails
import pipelines.auth.{SetRolesForUserRequest, UserRoles}
import pipelines.core.{GenericErrorResult, GenericMessageResult}

trait UserService[F[_]] { //extends LoginHandler[F] {
  def findUser(usernameOrEmail: String): F[Option[RegisteredUser]]
  def createUser(request: CreateUserRequest): F[Either[GenericErrorResult, GenericMessageResult]]
  def userRolesOpt(): F[Option[(VersionDetails, UserRoles)]]
  def updateUserRoles(updatingUser: String, request: SetRolesForUserRequest): F[Either[GenericErrorResult, GenericMessageResult]]
  def loginHandler: LoginHandler[F]
}
