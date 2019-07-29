package pipelines.client.jvm

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.SSLContext
import monix.execution.Ack
import monix.reactive.Observable
import pipelines.Env
import pipelines.rest.socket._

import scala.concurrent.Future

final class ClientSession private (val client: PipelinesClient[Future], val token: String, val socket: ClientSocket)(implicit env: Env)
    extends ClientSocketSessionState(env.ioScheduler)
    with StrictLogging {
  private implicit val execContext = env.ioScheduler

  override def messages: Observable[AddressedMessage] = {
    socket.fromServer.dump(s"client toClientOutput")
  }

  override protected def logInfo(msg: String): Unit = {
    logger.info(msg)
  }

  override protected def raiseError(msg: String): Unit = {
    sys.error(msg)
  }

  override protected def sendMessage(msg: AddressedMessage): Unit = {
    logger.info(s"Sending $msg")
    socket.sendAddressedMessage(msg)
  }

  messages.foreach { msg: AddressedMessage =>
    logger.info("\ttoClientOutput got " + msg)
  }

  socket.toServerOutput.dump(s"client toServerOutput").foreach { msg: AddressedMessage =>
    logger.info("\ttoClientOutput got " + msg)
  }

  def requestHandshake(): Future[Ack] = socket.requestHandshake()
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
