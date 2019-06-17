package pipelines.users

import pipelines.core.{BaseEndpoint, GenericMessageResult}

trait UserAuthEndpoints extends BaseEndpoint {

  object getUserAuth {
    def getRequest                                                             = get(path / "users" / segment[String]("userId") / "auth")
    def getResponse(implicit resp: JsonResponse[UserAuth]): Response[UserAuth] = jsonResponse[UserAuth]()
    def getEndpoint(implicit resp: JsonResponse[UserAuth]): Endpoint[Email, UserAuth] = endpoint(getRequest, getResponse)
  }
  object updateUserAuth {
    def postRequest(implicit req: JsonRequest[UserAuth]): Request[UserAuth]                                                                   = post(path / "users" / "auth", jsonRequest[UserAuth]())
    def postResponse(implicit resp: JsonResponse[GenericMessageResult]): Response[GenericMessageResult]                                       = jsonResponse[GenericMessageResult]()
    def postEndpoint(implicit req: JsonRequest[UserAuth], resp: JsonResponse[GenericMessageResult]): Endpoint[UserAuth, GenericMessageResult] = endpoint(postRequest, postResponse)
  }

}
