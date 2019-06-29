package pipelines.users

import java.time.ZonedDateTime
import java.util.UUID

import io.circe.Decoder.Result
import io.circe.generic.semiauto._
import io.circe.java8.time._
import io.circe.{Decoder, Encoder, Json, ObjectEncoder}

/**
  *
  * @param defaultHomePage
  * @param confirmId an Id which, if non-null/non-empty, identifies an ID which the user can use to acknowledge their account
  * @param confirmedOn the date at which the user has confirmed (presumably via a sent email) their account
  */
case class UserDetails(defaultHomePage: String, confirmId: String, confirmedOn: Option[ZonedDateTime]) {
  def isConfirmed: Boolean = confirmedOn.nonEmpty
}

object UserDetails extends JavaTimeDecoders with JavaTimeEncoders {
  def empty                                        = UserDetails("", UUID.randomUUID.toString, None)
  implicit def encoder: ObjectEncoder[UserDetails] = deriveEncoder[UserDetails]
  implicit def decoder                             = deriveDecoder[UserDetails]
}

final case class RegisteredUser(id: String, userName: UserName, email: Email, hashedPassword: String)
object RegisteredUser {
  implicit val encoder: ObjectEncoder[RegisteredUser] = io.circe.generic.semiauto.deriveEncoder[RegisteredUser]
  implicit val decoder: Decoder[RegisteredUser]       = io.circe.generic.semiauto.deriveDecoder[RegisteredUser]

}

final case class CreateUserRequest(userName: UserName, email: Email, hashedPassword: String) {
  def isValid(): Boolean = {
    userName.nonEmpty && InvalidEmailAddress.validate(email) && hashedPassword.nonEmpty
  }
}
object CreateUserRequest {
  val userNameField                                      = "userName"
  val emailField                                         = "email"
  implicit def encoder: ObjectEncoder[CreateUserRequest] = deriveEncoder[CreateUserRequest]
  implicit def decoder                                   = deriveDecoder[CreateUserRequest]
}

final case class CreateUserResponse(ok: Boolean, jwtToken: Option[String])
object CreateUserResponse {
  implicit def encoder = deriveEncoder[CreateUserResponse]
  implicit def decoder = deriveDecoder[CreateUserResponse]
}

final case class UpdateUserRequest(userOrEmail: Either[UserName, Email], details: UserDetails)
object UpdateUserRequest {
  implicit val encoder: Encoder[UpdateUserRequest] = {
    Encoder.instance[UpdateUserRequest] { request =>
      import io.circe.syntax._
      request.userOrEmail match {
        case Left(user) =>
          Json.obj("user" -> Json.fromString(user), "details" -> request.details.asJson)
        case Right(email) =>
          Json.obj("email" -> Json.fromString(email), "details" -> request.details.asJson)
      }
    }
  }
  implicit val decoder: Decoder[UpdateUserRequest] = Decoder.instance[UpdateUserRequest] { cursor =>
    val userEither: Result[Either[UserName, Email]] = {
      import cats.syntax.either._
      cursor.downField("user").as[String].map(Left.apply).orElse {
        cursor.downField("email").as[String].map(Right.apply)
      }
    }

    for {
      user    <- userEither
      details <- cursor.downField("details").as[UserDetails]
    } yield {
      UpdateUserRequest(user, details)
    }
  }
}

final case class UpdateUserResponse(ok: Boolean)
object UpdateUserResponse {
  implicit def encoder = deriveEncoder[UpdateUserResponse]
  implicit def decoder = deriveDecoder[UpdateUserResponse]
}

final case class LoginRequest(user: UserName, password: String)
object LoginRequest {
  implicit def encoder = deriveEncoder[LoginRequest]
  implicit def decoder = deriveDecoder[LoginRequest]
}

final case class LoginResponse(ok: Boolean, jwtToken: Option[String], redirectTo: Option[String])
object LoginResponse {
  implicit def encoder = deriveEncoder[LoginResponse]
  implicit def decoder = deriveDecoder[LoginResponse]
}
