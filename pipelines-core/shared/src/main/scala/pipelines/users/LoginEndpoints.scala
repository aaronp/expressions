package pipelines.users

import pipelines.core.BaseEndpoint

/**
* Keeping the login endpoint explicitly separate from the other user operations
  */
trait LoginEndpoints extends BaseEndpoint {

  /**
    * POST /users/login route for ... logging in existing users (see 'LoginHandler' for implementations)
    */
  object userLogin {

    def redirectHeader: RequestHeaders[Option[String]] = {
      optHeader(
        "redirectTo",
        Option(
          "Routes requiring authentication may redirect to login, in which case a successful " +
            "login will return a temporary redirect response to this route in addition to an X-Access-Token containing tokens to use in subsequent requests")
      )
    }

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

}
