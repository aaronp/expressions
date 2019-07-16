package pipelines.reactive

import io.circe.{Decoder, Json, ObjectEncoder}
import pipelines.rest.socket.AddressedMessage
import pipelines.users.Claims

final case class PushEvent(user : Claims, data: Json)

object PushEvent {
  implicit val encoder: ObjectEncoder[PushEvent] = io.circe.generic.semiauto.deriveEncoder[PushEvent]
  implicit val decoder: Decoder[PushEvent]       = io.circe.generic.semiauto.deriveDecoder[PushEvent]

  /**
    * A function which can transform a PushEvent to an AddressedMessage - this way sockets can listen to PushEvent messages
    * if we register this function as a transform
    *
    * @param event
    * @return
    */
  def asAddressedMessage(event: PushEvent): AddressedMessage = AddressedMessage(event)
}
