package pipelines.client.source

import io.circe.{Decoder, ObjectEncoder}

case class PushSourceState(createdBy: String, id: String, persist: Boolean)
object PushSourceState {
  implicit val encoder: ObjectEncoder[PushSourceState] = io.circe.generic.semiauto.deriveEncoder[PushSourceState]
  implicit val decoder: Decoder[PushSourceState]       = io.circe.generic.semiauto.deriveDecoder[PushSourceState]

}
