package pipelines.rest.socket

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.SSLContext
import pipelines.Env

import scala.concurrent.Future

object WebsocketClient extends StrictLogging {

  def apply(uri: String, env: Env, sslContextOpt: Option[SSLContext], settings: SocketSettings, authHeader: List[HttpHeader] = Nil): Future[ClientSocket] = {

    val wsReq = WebSocketRequest(uri, extraHeaders = authHeader)

    import env._
    val socket: ClientSocket = ClientSocket(settings)(env.ioScheduler)

    val http = Http()

    val connectionFuture = sslContextOpt match {
      case None =>
        val (future, _) = http.singleWebSocketRequest(wsReq, socket.akkaFlow)
        future
      case Some(sslContext) =>
        val ctxt: HttpsConnectionContext = {
          val https: HttpsConnectionContext = ConnectionContext.https(sslContext)
          http.setDefaultServerHttpContext(https)
          https
        }
        val (future, _) = http.singleWebSocketRequest(wsReq, socket.akkaFlow, connectionContext = ctxt)
        future
    }

    connectionFuture.map { upgrade =>
      require(
        upgrade.response.status.intValue == 101,
        s"Socket upgrade response to $uri w/ ${authHeader.size} headers was not upgrade (101), but ${upgrade.response.status}"
      )
      socket
    }(env.ioScheduler)
  }
}
