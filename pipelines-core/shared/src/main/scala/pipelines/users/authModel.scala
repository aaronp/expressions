package pipelines.users

import io.circe.{Decoder, ObjectEncoder}

case class AuthModel(permissionsByRole: Map[String, Set[String]]) {
  def roles: Set[String]       = permissionsByRole.keySet
  def permissions: Set[String] = permissionsByRole.values.flatten.toSet
}
object AuthModel {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[AuthModel]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[AuthModel]
}

case class SetRolesForUserRequest(version: Int, userId: String, roles: Set[String])
object SetRolesForUserRequest {
  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[SetRolesForUserRequest]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[SetRolesForUserRequest]
}

case class UserRoles(rolesByUserId: Map[String, Set[String]])
object UserRoles {
  implicit val encoder: ObjectEncoder[UserRoles] = io.circe.generic.semiauto.deriveEncoder[UserRoles]
  implicit val decoder: Decoder[UserRoles]       = io.circe.generic.semiauto.deriveDecoder[UserRoles]

}
