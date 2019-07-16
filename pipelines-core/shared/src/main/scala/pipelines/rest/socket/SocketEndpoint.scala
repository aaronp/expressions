package pipelines.rest.socket

import pipelines.core.{BaseEndpoint, GenericMessageResult}

trait SocketEndpoint extends BaseEndpoint {

  /**
    * opens up a socket
    *
    * GET /sockets/connect
    */
  object sockets {
    def request: Request[Unit] = get(path / "sockets" / "connect")

    def response(resp: JsonResponse[String]): Response[String] = jsonResponse[String](Option("either upgrades to a websocket or returns a socket id for an open socket"))(resp)

    def connect(resp: JsonResponse[String]): Endpoint[Unit, String] =
      endpoint(
        request,
        response(resp),
        description = Option("First use the sockets/token with an authenticated user to generate a token to pass in as the socket protocol for connecting")
      )
  }

  object socketTokens {
    def request: Request[Unit] = get(path / "sockets" / "token")

    def response(implicit resp: JsonResponse[String]): Response[String] =
      jsonResponse[String](Option("Return a token which can be used as a protocol for opening a websocket"))(resp)

    def newToken(implicit resp: JsonResponse[String]): Endpoint[Unit, String] = endpoint(request, response(resp))
  }

  object socketSubscribe {
    def request(implicit req: JsonRequest[SocketSubscribeRequest]): Request[SocketSubscribeRequest] = {
      post(path / "sockets" / "subscribe", jsonRequest[SocketSubscribeRequest]())
    }

    def response(implicit resp: JsonResponse[SocketSubscribeResponse]): Response[SocketSubscribeResponse] =
      jsonResponse[SocketSubscribeResponse](Option("The subscription message - just an 'yeah, ok' response"))(resp)

    def subscribe(implicit req: JsonRequest[SocketSubscribeRequest], resp: JsonResponse[SocketSubscribeResponse]): Endpoint[SocketSubscribeRequest, SocketSubscribeResponse] = {
      endpoint(request, response(resp))
    }
  }
  object socketUnsubscribe {
    def request(implicit req: JsonRequest[SocketUnsubscribeRequest]): Request[SocketUnsubscribeRequest] = {
      post(path / "sockets" / "unsubscribe", jsonRequest[SocketUnsubscribeRequest]())
    }

    def response(implicit resp: JsonResponse[GenericMessageResult]): Response[GenericMessageResult] =
      jsonResponse[GenericMessageResult](Option("The subscription message - just an 'yeah, ok' response"))(resp)

    def unsubscribe(implicit req: JsonRequest[SocketUnsubscribeRequest], resp: JsonResponse[GenericMessageResult]): Endpoint[SocketUnsubscribeRequest, GenericMessageResult] = {
      endpoint(request, response(resp))
    }
  }
}
