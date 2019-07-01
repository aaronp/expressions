package pipelines.users

import pipelines.audit.VersionDetails
import pipelines.auth.{SetRolesForUserRequest, UserRoles}
import pipelines.core.{BaseEndpoint, GenericMessageResult}

trait UserRoleEndpoints extends BaseEndpoint {

  object listUserRoles {
    type Result = Option[(VersionDetails, UserRoles)]
    def getRequest: Request[Unit] = get(path / "users")
    def getResponse(implicit resp: JsonResponse[Result]): Response[Result] = {
      jsonResponse[Result]()
    }
    def getEndpoint(implicit resp: JsonResponse[Result]): Endpoint[Unit, Result] = {
      endpoint(getRequest, getResponse)
    }
  }

  object updateUserRoles {
    def postRequest(implicit req: JsonRequest[SetRolesForUserRequest]): Request[SetRolesForUserRequest] = post(path / "users" / "auth", jsonRequest[SetRolesForUserRequest]())
    def postResponse(implicit resp: JsonResponse[GenericMessageResult]): Response[GenericMessageResult] = jsonResponse[GenericMessageResult]()
    def postEndpoint(implicit req: JsonRequest[SetRolesForUserRequest], resp: JsonResponse[GenericMessageResult]): Endpoint[SetRolesForUserRequest, GenericMessageResult] =
      endpoint(postRequest, postResponse)
  }

}
