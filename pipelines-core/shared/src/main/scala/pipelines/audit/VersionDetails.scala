package pipelines.audit
import java.time.ZonedDateTime

import io.circe.{Decoder, ObjectEncoder}

case class VersionDetails(override val revision: Int, override val createdAt: Long, override val userId: String) extends HasVersionDetails {
  override def version = this
}
object VersionDetails {
  def apply(revision: Int, createdAt: ZonedDateTime, userId: String) = {
    new VersionDetails(revision, createdAt.toInstant.toEpochMilli, userId)
  }

  implicit val encoder: ObjectEncoder[VersionDetails] = io.circe.generic.semiauto.deriveEncoder[VersionDetails]
  implicit val decoder: Decoder[VersionDetails]       = io.circe.generic.semiauto.deriveDecoder[VersionDetails]
}
