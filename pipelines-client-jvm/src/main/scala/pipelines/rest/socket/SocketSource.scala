package pipelines.rest.socket

import monix.reactive.Observable
import pipelines.reactive.{ContentType, DataSource}
import pipelines.users.Claims

object SocketSource {
  val contentType: ContentType = ContentType.of[(Claims, AddressedMessage)]
}

/**
  * A data source whose data will be sent to the remote client
  * @param user
  * @param socket
  * @param metadata
  */
final case class SocketSource(user: Claims, socket: ServerSocket, override val metadata: Map[String, String]) extends DataSource {
  override type T = SocketSource

  override def addMetadata(entries: Map[String, String]): SocketSource = copy(metadata = metadata ++ entries)

  override val contentType = SocketSource.contentType

  def socketAndUserData: Observable[(Claims, AddressedMessage)] = socketData.map(user -> _)

  def socketData: Observable[AddressedMessage] = socket.dataFromClientOutput

  override def data(ct: ContentType): Option[Observable[_]] = {
    if (ct == contentType) {
      Option(socketAndUserData)
    } else if (ct == ContentType.of[Claims]) {
      Option(socket.dataFromClientOutput.map(_ => user))
    } else if (ct == ContentType.of[AddressedMessage]) {
      Option(socketData)
    } else {
      None
    }
  }
}
