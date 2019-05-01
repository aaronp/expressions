package pipelines.socket

import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.ws.WebSocketRequest
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import com.typesafe.scalalogging.StrictLogging
import javax.net.ssl.SSLContext
import pipelines.Env

import scala.concurrent.Future

object WebsocketClient extends StrictLogging {

  def apply(uri: String, env: Env, sslContextOpt: Option[SSLContext], settings: SocketSettings, authHeader: Option[Authorization] = None): Future[ClientSocket] = {

    val wsReq = WebSocketRequest(uri, extraHeaders = authHeader.toList)

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
      logger.info(s"Socket upgrade response is ${upgrade.response.status}")
      socket
    }(env.ioScheduler)
  }
}
