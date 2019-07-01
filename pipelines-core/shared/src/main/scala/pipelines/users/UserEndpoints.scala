package pipelines.users

import pipelines.core.{BaseEndpoint, GenericMessageResult}

/**
  * Endpoints for user actions (e.g. login, logout, update profile, ...)
  */
trait UserEndpoints extends BaseEndpoint {

  /** Confirm a new user via a GET request w/ a specific token - presumably having followed a link from an email
    */
  object confirmUser {

    def confirmUserRequest: Request[String] = {
      get(path / "users" / "confirm" /? qs[String]("confirmationId", Option("The confirmation id used to acknowledge a user's email is valid")))
    }

    def confirmUserResponse = emptyResponse(Option("The status code 200 indicates confirmation success"))

    def confirmEndpoint: Endpoint[String, Unit] = {
      endpoint(confirmUserRequest, confirmUserResponse)
    }
  }

  /**
    * Gets the user's current status based on the JWT token passed in
    */
  object userStatus {
    def statusRequest: Request[Unit] = get(path / "users")

    def statusResponse(implicit resp: JsonResponse[UserStatusResponse]): Response[UserStatusResponse] = jsonResponse[UserStatusResponse]()

    def statusEndpoint(implicit resp: JsonResponse[UserStatusResponse]): Endpoint[Unit, UserStatusResponse] = {
      endpoint(statusRequest, statusResponse)
    }
  }

  /**
    * Create a new user
    */
  object createUser {
    def createUserRequest(implicit req: JsonRequest[CreateUserRequest]): Request[CreateUserRequest] = {
      post(path / "users" / "create", jsonRequest[CreateUserRequest]())
    }
    def createUserResponse(implicit resp: JsonResponse[CreateUserResponse]): Response[CreateUserResponse] = jsonResponse[CreateUserResponse]()

    def createUserEndpoint(implicit req: JsonRequest[CreateUserRequest], resp: JsonResponse[CreateUserResponse]): Endpoint[CreateUserRequest, CreateUserResponse] = {
      endpoint(createUserRequest, createUserResponse)
    }
  }

  /**
    * Resets a user's password
    */
  object resetUser {
    def resetUserRequest(implicit req: JsonRequest[UserReminderRequest]): Request[UserReminderRequest] = {
      post(path / "users" / "reset", jsonRequest[UserReminderRequest]())
    }
    def resetUserResponse(implicit resp: JsonResponse[GenericMessageResult]): Response[GenericMessageResult] = jsonResponse[GenericMessageResult]()

    def resetUserEndpoint(implicit req: JsonRequest[UserReminderRequest], resp: JsonResponse[GenericMessageResult]) = {
      endpoint(resetUserRequest, resetUserResponse)
    }
  }
}
