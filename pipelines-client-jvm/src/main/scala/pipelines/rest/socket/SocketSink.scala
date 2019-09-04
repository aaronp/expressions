package pipelines.rest.socket

import monix.execution.{Cancelable, Scheduler}
import monix.reactive.{Observable, Observer}
import pipelines.reactive.{ContentType, DataSink}
import pipelines.users.Claims

/**
  * A 'DataSink' which gets registered (together with a SocketSource) when a new websocket connection is made
  *
  * @param user
  * @param socket
  * @param metadata
  */
final case class SocketSink(user: Claims, socket: ServerSocket, override val metadata: Map[String, String]) extends DataSink {
  override type T = SocketSink

  override def addMetadata(entries: Map[String, String]): SocketSink = copy(metadata = metadata ++ entries)

  override type Input         = AddressedMessage
  override type Output        = Unit
  override type ConnectResult = Cancelable

  override val inputType: ContentType = ContentType.of[AddressedMessage]

  def toClient: Observer[AddressedMessage] = socket.toClient
  override def connect(contentType: ContentType, observable: Observable[AddressedMessage], sourceMetadata: Map[String, String])(implicit scheduler: Scheduler): Cancelable = {
    observable.subscribe(toClient)
  }
}
