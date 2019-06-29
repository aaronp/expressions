package pipelines.users

import pipelines.core.BaseEndpoint

/**
  * Endpoints for user actions (e.g. login, logout, update profile, ...)
  */
trait UserEndpoints extends BaseEndpoint {

  def redirectHeader: RequestHeaders[Option[String]] = {
    optHeader(
      "redirectTo",
      Option(
        "Routes requiring authentication may redirect to login, in which case a successful " +
          "login will return a temporary redirect response to this route in addition to an X-Access-Token containing tokens to use in subsequent requests")
    )
  }

  /**
    * POST /users/login route for ... logging in existing users (see 'LoginHandler' for implementations)
    */
  object userLogin {
    def loginRequest(implicit req: JsonRequest[LoginRequest]): Request[(LoginRequest, Option[String])] = {
      post(path / "users" / "login", jsonRequest[LoginRequest](Option("Basic user login request")), redirectHeader)
    }

    /**
      * Get the counter current value.
      * Uses the HTTP verb “GET” and URL path “/current-value”.
      * The response entity is a JSON document representing the counter value.
      */
    def loginEndpoint(implicit req: JsonRequest[LoginRequest], resp: JsonResponse[LoginResponse]): Endpoint[(LoginRequest, Option[String]), LoginResponse] =
      endpoint(loginRequest, loginResponse)

  }

  protected def loginResponse(implicit resp: JsonResponse[LoginResponse]): Response[LoginResponse] =
    jsonResponse[LoginResponse](Option("A login response which will also include an X-Access-Token header to use in subsequent requests"))

  /** Confirm a new user via a GET request w/ a specific token - presumably having followed a link from an email
    */
  object ack {

    def ackUserRequest: Request[String] = {
      get(path / "users" / "confirm" /? qs[String]("confirmationId", Option("The confirmation id used to acknowledge a user's email is valid")))
    }

    def ackUserResponse = emptyResponse(Option("The status code 200 indicates confirmation success"))

    def ackEndpoint: Endpoint[String, Unit] = {
      endpoint(ackUserRequest, ackUserResponse)
    }
  }

  def createUserRequest(implicit req: JsonRequest[CreateUserRequest]): Request[CreateUserRequest] = {
    post(path / "users" / "create", jsonRequest[CreateUserRequest]())
  }
  def createUserResponse(implicit resp: JsonResponse[CreateUserResponse]): Response[CreateUserResponse] = jsonResponse[CreateUserResponse]()

  def createUserEndpoint(implicit req: JsonRequest[CreateUserRequest], resp: JsonResponse[CreateUserResponse]): Endpoint[CreateUserRequest, CreateUserResponse] = {
    endpoint(createUserRequest, createUserResponse)
  }
}
