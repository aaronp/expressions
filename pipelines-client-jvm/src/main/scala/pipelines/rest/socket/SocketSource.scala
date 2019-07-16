package pipelines.rest.socket

import monix.reactive.Observable
import pipelines.reactive.{ContentType, DataSource}
import pipelines.users.Claims

object SocketSource {
  val contentType: ContentType = ContentType.of[AddressedMessage]
}

final case class SocketSource(user: Claims, socket: ServerSocket, override val metadata: Map[String, String]) extends DataSource {
  override type T = SocketSource

  override def addMetadata(entries: Map[String, String]): SocketSource = copy(metadata = metadata ++ entries)

  override val contentType = SocketSource.contentType

  override def data(ct: ContentType): Option[Observable[_]] = {
    if (ct == contentType) {
      Option(socket.toRemoteAkkaInput)
    } else {
      None
    }
  }
}