package pipelines.client.jvm

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import javax.net.ssl.SSLContext
import monix.execution.Ack
import pipelines.Env
import pipelines.rest.socket.{ClientSocket, SocketSettings, WebsocketClient}

import scala.concurrent.Future
import scala.reflect.ClassTag

final class ClientSession private (client: PipelinesClient[Future], val token: String, val socket: ClientSocket)(implicit env: Env) extends StrictLogging {
  private implicit val execContext = env.ioScheduler

  socket.fromServer.dump(s"client toClientOutput").foreach { msg =>
    logger.info("\ttoClientOutput got " + msg)
  }
  socket.toServerOutput.dump(s"client toServerOutput").foreach { msg =>
    logger.info("\ttoClientOutput got " + msg)
  }

  def requestHandshake(): Future[Ack] = socket.requestHandshake()

  def send[T: ClassTag: Encoder](data: T): Future[Ack] = {
    logger.info(s"Sending $data")
    socket.send(data)
  }
}

object ClientSession {

  def apply(client: PipelinesClient[Future], token: String, sslContext: Option[SSLContext], wsUri: String, userName: String)(implicit env: Env): Future[ClientSession] = {
    apply(client, token, sslContext, wsUri, SocketSettings(userName))
  }

  def apply(client: PipelinesClient[Future], token: String, sslContext: Option[SSLContext], wsUri: String, settings: SocketSettings)(implicit env: Env): Future[ClientSession] = {

    val headers = List(
      Authorization(OAuth2BearerToken(token)),
      RawHeader("x-access-token", token)
    )
    val wsClientFuture: Future[ClientSocket] = WebsocketClient(wsUri, env, sslContext, settings, headers)

    implicit val ec = env.ioScheduler

    wsClientFuture.map { socket =>
      val session = new ClientSession(client, token, socket)
      session.requestHandshake()
      session
    }
  }
}
