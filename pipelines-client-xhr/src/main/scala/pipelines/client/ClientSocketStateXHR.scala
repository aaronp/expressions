package pipelines.client

import io.circe
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer, Pipe}
import org.scalajs.dom
import org.scalajs.dom.raw.{MessageEvent, WebSocket}
import pipelines.rest.socket.{AddressedMessage, ClientSocketState, SocketConnectionAckRequest}

import scala.util.control.NonFatal

/**
  * This class wraps access to an underly WebSocket, the responsibility
  * being to keep track of subscriptions and access/filtering/routing of messages over a single socket
  *
  * @param socket
  */
final class ClientSocketStateXHR private (socket: WebSocket) extends ClientSocketState(Scheduler.global) {

  /**
    * Let's drive the socket access via our good friend, the Monix Pipe
    */
  val (input: Observer[MessageEvent], messageEvents: Observable[MessageEvent]) = {
    val (a, b) = Pipe.publish[MessageEvent].multicast(scheduler)
    (a, b.dump("ClientSocketState"))
  }

  /**
    * All the addressed messages coming from the server.
    *
    * As we can only open a single socket, the messages are wrapped as AddressedMessage so that
    * individual pieces of the UI can filter on the parts intended for them
    */
  override val messages: Observable[AddressedMessage] = messageEvents.collect {
    case ClientSocketStateXHR.AsAddressMessage(msg) => msg
  }

  def sendMessage(data: AddressedMessage): Unit = {
    import io.circe.syntax._
    HtmlUtils.log(s"sendMessage(${data})")
    socket.send(data.asJson.noSpaces)
  }

  def close(code: Int = 0): Unit = socket.close(code)

  socket.onerror = { evt =>
    HtmlUtils.log(s"onError ${evt}")
    input.onError(new Exception(s"Socket error: $evt"))
  }
  socket.onmessage = { msg =>
    HtmlUtils.log(s"onMessage: ${msg.data}")
    input.onNext(msg)
  }
  socket.onopen = { msg =>
    HtmlUtils.log(s"onopen ${msg}")
    send(SocketConnectionAckRequest(true))
  }

  override protected def logInfo(msg: String): Unit = {
    HtmlUtils.log(msg)
  }

  override protected def raiseError(msg: String): Unit = {
    HtmlUtils.raiseError(msg)
  }
}
object ClientSocketStateXHR {
  def apply(relativeSocketHref: String, connectionToken: String): ClientSocketStateXHR = {
    val socketUrl = {
      val wsProtocol = dom.window.location.protocol.replaceAllLiterally("http", "ws")
      s"${wsProtocol}//${dom.window.location.host}${relativeSocketHref}"
    }
    HtmlUtils.log(s"Socket url is: " + socketUrl + " connecting with " + connectionToken)

    // TODO - improve the hand-shake to use a custom connection protocol.
    // see e.g. Stack Overflow: https://stackoverflow.com/questions/4361173/http-headers-in-websockets-client-api
    new ClientSocketStateXHR(new WebSocket(socketUrl, connectionToken))
  }

  private object AsAddressMessage {
    def unapply(event: MessageEvent): Option[AddressedMessage] = {
      try {

        val either: Either[circe.Error, AddressedMessage] = io.circe.parser.decode[AddressedMessage](event.data.toString)
        either match {
          case Left(err) =>
            HtmlUtils.debug(s"Could't parse the socket message event '${event.data}' into an AddressedMessage: $err")
            None
          case Right(msg) => Option(msg)
        }

      } catch {
        case NonFatal(e) =>
          HtmlUtils.debug(s"Could't turn a socket message event '${event.data}' into an AddressedMessage: $e")
          None
      }
    }
  }

}
