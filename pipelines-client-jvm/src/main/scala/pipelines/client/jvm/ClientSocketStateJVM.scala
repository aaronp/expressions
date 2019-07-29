package pipelines.client.jvm

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken, RawHeader}
import com.softwaremill.sttp
import com.softwaremill.sttp.MonadError
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import javax.net.ssl.SSLContext
import monix.execution.Ack
import monix.reactive.Observable
import pipelines.Env
import pipelines.reactive.repo.PushSourceResponse
import pipelines.rest.socket._

import scala.concurrent.Future

final class ClientSocketStateJVM private (val client: PipelinesClient[Future], val token: String, val socket: ClientSocket, userName: String)(implicit env: Env)
    extends ClientSocketState(env.ioScheduler)
    with StrictLogging {
  private implicit val execContext = env.ioScheduler

  override val messages: Observable[AddressedMessage] = {
    socket.fromServer.dump("client toClientOutput")
  }
  val messages2: Observable[AddressedMessage] = {
    socket.toServerOutput.dump("client toServerOutput")
  }

  override protected def logInfo(msg: String): Unit = {
    logger.info(msg)
  }

  override protected def raiseError(msg: String): Unit = {
    sys.error(msg)
  }

  def pushToSource(data: Json = Json.Null,
                   name: String = userName,
                   createIfMissing: Boolean = true,
                   persist: Boolean = false,
                   metadata: Map[String, String] = Map.empty): Future[PushSourceResponse] = {
    import client._
    val queryParams                                       = (name, Option(createIfMissing), Option(persist))
    val sttpReq: client.SttpRequest                       = appendHeaders(client.pushSource.request.apply(queryParams -> data), metadata)
    val request                                           = sttpReq.response(client.pushSource.response.responseAs)
    val pushResp: client.SttpResponse[PushSourceResponse] = client.pushSource.response
    monadError.flatMap(castAsFuture(client.backend.send(request))) {
      case casted: sttp.Response[pushResp.ReceivedBody] => pushResp.validateResponse(casted)
    }
  }

  private def appendHeaders(sttpReq: client.SttpRequest, additionalHeaders: Map[String, String]): client.SttpRequest = {
    val newHeaders = additionalHeaders.updated("Authorization", s"Bearer $token").updated("X-Access-Token", token)
    sttpReq.headers(newHeaders)
  }
  private def castAsFuture[F[_], T](r: F[T]): Future[T] = r.asInstanceOf[Future[T]]

  private def monadError: MonadError[Future] = client.backend.responseMonad.asInstanceOf[MonadError[Future]]

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

object ClientSocketStateJVM {

  def apply(client: PipelinesClient[Future], token: String, sslContext: Option[SSLContext], wsUri: String, userName: String)(implicit env: Env): Future[ClientSocketStateJVM] = {
    apply(client, token, sslContext, wsUri, SocketSettings(userName))
  }

  def apply(client: PipelinesClient[Future], token: String, sslContext: Option[SSLContext], wsUri: String, settings: SocketSettings)(
      implicit env: Env): Future[ClientSocketStateJVM] = {

    val headers = List(
      Authorization(OAuth2BearerToken(token)),
      RawHeader("x-access-token", token)
    )
    val wsClientFuture: Future[ClientSocket] = WebsocketClient(wsUri, env, sslContext, settings, headers)

    implicit val ec = env.ioScheduler

    wsClientFuture.map { socket: ClientSocket =>
      val session = new ClientSocketStateJVM(client, token, socket, settings.name)
      session.requestHandshake()
      session
    }
  }
}
