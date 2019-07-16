package pipelines.reactive

import io.circe.{Decoder, ObjectEncoder}
import pipelines.users.Claims

final case class UploadEventDTO(user: Claims, fileName: String, size: Long)
object UploadEventDTO {
  implicit val encoder: ObjectEncoder[UploadEventDTO] = io.circe.generic.semiauto.deriveEncoder[UploadEventDTO]
  implicit val decoder: Decoder[UploadEventDTO]       = io.circe.generic.semiauto.deriveDecoder[UploadEventDTO]
}
