package pipelines.rest.socket

import io.circe.{Decoder, ObjectEncoder}

/**
* Generic representation of an event
  */
final case class EventDto(event : String, data : Map[String, String])

object EventDto {
  implicit val encoder: ObjectEncoder[EventDto] = io.circe.generic.semiauto.deriveEncoder[EventDto]
  implicit val decoder: Decoder[EventDto] = io.circe.generic.semiauto.deriveDecoder[EventDto]

}
