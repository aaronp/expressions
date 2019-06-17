package pipelines.audit

import java.time.ZonedDateTime

import io.circe.Json

case class AuditVersion(revision: Int, createdAt: Long, userId: String, record: Json)

object AuditVersion {

  def apply(revision: Int, createdAt: ZonedDateTime, userId: String, record: Json) = {
    new AuditVersion(revision, createdAt.toInstant.toEpochMilli, userId, record)
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[AuditVersion]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[AuditVersion]
}
