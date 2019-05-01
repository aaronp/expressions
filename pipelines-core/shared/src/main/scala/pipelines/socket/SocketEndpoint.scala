package pipelines.socket

import pipelines.core.BaseEndpoint

trait SocketEndpoint extends BaseEndpoint {

  /**
    * opens up a socket
    *
    * GET /sockets/connect
    */
  object sockets {
    def request: Request[Unit] = get(path / "sockets" / "connect")

    def response(resp: JsonResponse[String]): Response[String] = jsonResponse[String](Option("either upgrades to a websocket or returns a socket id for an open socket"))(resp)

    def connect(resp: JsonResponse[String]): Endpoint[Unit, String] = endpoint(request, response(resp))
  }
}
