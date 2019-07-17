package pipelines.client.jvm

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.SSLContext
import pipelines.Env
import pipelines.rest.socket.{ClientSocket, SocketSettings, WebsocketClient}

import scala.concurrent.Future

final class ClientSession[R[_]](client: PipelinesClient[R], token: String, val socket: ClientSocket)(implicit env: Env) extends StrictLogging {
  private implicit val execContext = env.ioScheduler
  socket.toClientOutput.dump(s"client toClientOutput").foreach { msg =>
    logger.info("\ttoClientOutput got " + msg)
  }
  socket.toServerOutput.dump(s"client toServerOutput").foreach { msg =>
    logger.info("\ttoClientOutput got " + msg)
  }
}

object ClientSession {

  def apply[R[_]](client: PipelinesClient[R], token: String, sslContext: Option[SSLContext], wsUri: String, userName: String)(implicit env: Env) : Future[ClientSession[R]] = {
    apply[R](client, token, sslContext, wsUri, SocketSettings(userName))
  }

  def apply[R[_]](client: PipelinesClient[R], token: String, sslContext: Option[SSLContext], wsUri: String, settings: SocketSettings)(implicit env: Env): Future[ClientSession[R]] = {
    val wsClientFuture: Future[ClientSocket] = WebsocketClient(wsUri, env, sslContext, settings, Option(Authorization(OAuth2BearerToken(token))))

    implicit val ec = env.ioScheduler
    wsClientFuture.map { socket =>
      new ClientSession[R](client, token, socket)
    }
  }
}
