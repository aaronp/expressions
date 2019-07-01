package pipelines.users

import pipelines.core.{BaseEndpoint, GenericMessageResult}

trait UserAuthEndpoints extends BaseEndpoint {

  object getUserAuth {
    def getRequest                                                                                                = get(path / "users" / segment[String]("userId") / "auth")
    def getResponse(implicit resp: JsonResponse[SetRolesForUserRequest]): Response[SetRolesForUserRequest]        = jsonResponse[SetRolesForUserRequest]()
    def getEndpoint(implicit resp: JsonResponse[SetRolesForUserRequest]): Endpoint[Email, SetRolesForUserRequest] = endpoint(getRequest, getResponse)
  }
  object updateUserAuth {
    def postRequest(implicit req: JsonRequest[SetRolesForUserRequest]): Request[SetRolesForUserRequest] = post(path / "users" / "auth", jsonRequest[SetRolesForUserRequest]())
    def postResponse(implicit resp: JsonResponse[GenericMessageResult]): Response[GenericMessageResult] = jsonResponse[GenericMessageResult]()
    def postEndpoint(implicit req: JsonRequest[SetRolesForUserRequest], resp: JsonResponse[GenericMessageResult]): Endpoint[SetRolesForUserRequest, GenericMessageResult] =
      endpoint(postRequest, postResponse)
  }

}
