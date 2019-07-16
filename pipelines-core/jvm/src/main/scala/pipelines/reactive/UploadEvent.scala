package pipelines.reactive

import java.nio.file.Path

import pipelines.rest.socket.AddressedMessage
import pipelines.users.Claims

/**
  * Represents a user-upload. This isn't serializable as-is: it's meant/intended to be mapped by various transforms into another type
  *
  * @param user
  * @param uploadPath
  * @param fileName
  */
final case class UploadEvent(user: Claims, uploadPath: Path, fileName: String) {
  def asDto: UploadEventDTO = {
    import eie.io._
    new UploadEventDTO(user, fileName, uploadPath.size)

  }
}

object UploadEvent {

  /**
    * A function which can transform a PushEvent to an AddressedMessage - this way sockets can listen to PushEvent messages
    * if we register this function as a transform
    *
    * @param event
    * @return
    */
  def asAddressedMessage(event: UploadEvent): AddressedMessage = AddressedMessage(event.asDto)
}
