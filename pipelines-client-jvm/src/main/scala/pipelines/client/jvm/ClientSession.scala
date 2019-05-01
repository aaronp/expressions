package pipelines.client.jvm

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import javax.net.ssl.SSLContext
import pipelines.Env
import pipelines.socket.{ClientSocket, SocketSettings, WebsocketClient}

import scala.concurrent.Future

case class ClientSession[R[_]](client: PipelinesClient[R], token: String, sslContext: Option[SSLContext], wsUri: String, userName: String) {

  def socket(settings: SocketSettings = SocketSettings(userName, s"session for $userName"))(implicit env: Env): Future[ClientSocket] = {

    WebsocketClient(wsUri, env, sslContext, settings, Option(Authorization(OAuth2BearerToken(token))))
  }
}
