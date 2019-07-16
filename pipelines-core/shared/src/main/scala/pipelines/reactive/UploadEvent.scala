package pipelines.reactive

import java.nio.file.Path

import io.circe.{Decoder, ObjectEncoder}
import pipelines.rest.socket.AddressedMessage
import pipelines.users.Claims

/**
  * Represents a user-upload. This isn't serializable as-is: it's meant/intended to be mapped by various transforms into another type
  *
  * @param user
  * @param uploadPath
  * @param fileName
  */
final case class UploadEvent(user: Claims, uploadPath: Path, fileName: String)

object UploadEvent {

  final case class UploadDTO(user: Claims, fileName: String, size: Long)
  object UploadDTO {
    def apply(event: UploadEvent) = {
      import eie.io._
      new UploadDTO(event.user, event.fileName, event.uploadPath.size)
    }
    implicit val encoder: ObjectEncoder[UploadDTO] = io.circe.generic.semiauto.deriveEncoder[UploadDTO]
    implicit val decoder: Decoder[UploadDTO]       = io.circe.generic.semiauto.deriveDecoder[UploadDTO]

  }

  /**
    * A function which can transform a PushEvent to an AddressedMessage - this way sockets can listen to PushEvent messages
    * if we register this function as a transform
    *
    * @param event
    * @return
    */
  def asAddressedMessage(event: UploadEvent): AddressedMessage = {
    AddressedMessage(UploadDTO(event))
  }
}
