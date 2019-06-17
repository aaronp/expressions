package pipelines.users

import pipelines.core.{BaseEndpoint, GenericMessageResult}

trait AuthEndpoints extends BaseEndpoint {

  object updateAuth {
    def updateRequest(implicit req: JsonRequest[AuthModel]): Request[AuthModel] = post(path / "auth", jsonRequest[AuthModel]())
    def updateResponse(implicit resp: JsonResponse[GenericMessageResult]): Response[GenericMessageResult] = jsonResponse[GenericMessageResult]()
    def updateEndpoint(implicit req: JsonRequest[AuthModel], resp: JsonResponse[GenericMessageResult]): Endpoint[AuthModel, GenericMessageResult] = {
      endpoint(
        updateRequest,
        updateResponse)
    }
  }

  object getAuth {
    def getRequest: Request[Unit] = get(path / "auth")
    def getResponse(implicit resp: JsonResponse[AuthModel]): Response[AuthModel] = jsonResponse[AuthModel]()
    def getEndpoint(implicit resp: JsonResponse[AuthModel]): Endpoint[Unit, AuthModel] = endpoint(getRequest, getResponse)
  }
}
