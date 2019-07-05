package pipelines.client

import java.util.UUID

import io.circe
import io.circe.{Decoder, Encoder}
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}
import org.scalajs.dom
import org.scalajs.dom.raw.{MessageEvent, WebSocket}
import pipelines.rest.socket.{AddressedMessage, SocketConnectionAck, SocketSubscribeRequest, SocketUnsubscribeRequest}

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * This class wraps access to an underly WebSocket, the responsibility
  * being to keep track of subscriptions and access/filtering/routing of messages over a single socket
  *
  * @param socket
  */
final class ClientSocketState private (socket: WebSocket) {
  implicit val socketScheduler = Scheduler.global

  /**
    * Let's drive the socket access via our good friend, the Monix Pipe
    */
  val (input: Observer[MessageEvent], messageEvents: Observable[MessageEvent]) = {
    val (a, b) = Pipe.replayLimited[MessageEvent](10).multicast
    (a, b.dump("SocketState"))
  }

  messageEvents.foreach { evnt =>
    HtmlUtils.log(s"\tsocket output:  ${evnt}")
  }

  /**
    * All the addressed messages coming from the server.
    *
    * As we can only open a single socket, the messages are wrapped as AddressedMessage so that
    * individual pieces of the UI can filter on the parts intended for them
    */
  val messages: Observable[AddressedMessage] = messageEvents.collect {
    case ClientSocketState.AsAddressMessage(msg) => msg
  }

  /**
    * Immediately subscribe for our handshake ack, which we can use to add/remove subscriptions to our socket source/sink.
    */
  private val connectionAck: CancelableFuture[Try[SocketConnectionAck]] = observerOf[SocketConnectionAck].headL.runToFuture

  def subscribe(sourceCriteria: Map[String, String], addressedMessageId: String = UUID.randomUUID.toString, transforms: Seq[String] = Nil): String = {
    connectionAck.foreach {
      case Success(ack: SocketConnectionAck) =>
        HtmlUtils.log(s"Subscribing w/ $addressedMessageId to socket ${ack.commonId}")
        send(SocketSubscribeRequest(ack.commonId, sourceCriteria, addressedMessageId, transforms))
      case Failure(err) =>
        HtmlUtils.raiseError(s"Failure getting socket ack: $err")
    }
    addressedMessageId
  }

  def unSubscribe(subscriptionId: String): Unit = {
    HtmlUtils.log(s"Unsubscribing w/ $subscriptionId from socket")
    send(SocketUnsubscribeRequest(subscriptionId))
  }

  def send[T: ClassTag: Encoder](data: T): Unit = {
    val msg: AddressedMessage = AddressedMessage(data)
    sendMessage(msg)
  }

  /**
    * Convenience to filter [[AddressedMessage]]s coming through the web socket on a particular 'to' topic
    * which corresponds to a classname, as well as trying to unmarshal the body of the message as the 'T' type
    *
    * @tparam T
    * @return a stream of 'T' messages
    */
  def observerOf[T: ClassTag: Decoder]: Observable[Try[T]] = {
    val topicName = AddressedMessage.topics.forClass[T]
    messages.filter(_.to == topicName).map(_.as[T])
  }

  def sendMessage(data: AddressedMessage): Unit = {
    import io.circe.syntax._
    socket.send(data.asJson.noSpaces)
  }

  def close(code: Int = 0): Unit = socket.close(code)

  socket.onerror = { evt =>
    HtmlUtils.log(s"onError ${evt}")
    input.onError(new Exception(s"Socket error: $evt"))
  }
  socket.onmessage = { msg =>
    input.onNext(msg)
    HtmlUtils.log(s"onMessage ${msg}")
  }
  socket.onopen = { msg =>
    HtmlUtils.log(s"onopen ${msg}")
  }
}
object ClientSocketState {
  def apply(relativeSocketHref: String, connectionToken: String): ClientSocketState = {
    val socketUrl = {
      val wsProtocol = dom.window.location.protocol.replaceAllLiterally("http", "ws")
      s"${wsProtocol}//${dom.window.location.host}${relativeSocketHref}"
    }
    HtmlUtils.log(s"Socket url is: " + socketUrl + " connecting with " + connectionToken)

    // TODO - improve the hand-shake to use a custom connection protocol.
    // see e.g. Stack Overflow: https://stackoverflow.com/questions/4361173/http-headers-in-websockets-client-api
    new ClientSocketState(new WebSocket(socketUrl, connectionToken))
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
