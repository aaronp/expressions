package pipelines.audit

import java.time.ZonedDateTime

import io.circe.Json


/**
  *
  * @param record the data
  */
case class AuditVersion(override val version: VersionDetails, record: Json) extends HasVersionDetails

object AuditVersion {

  val RevisionField = "version.revision"
  val CreatedAtField = "version.createdAt"
  val UserIdField = "version.userId"

  def apply(revision: Int, createdAt: ZonedDateTime, userId: String, record: Json): AuditVersion = {
    new AuditVersion(VersionDetails(revision, createdAt.toInstant.toEpochMilli, userId), record)
  }

  implicit val encoder = io.circe.generic.semiauto.deriveEncoder[AuditVersion]
  implicit val decoder = io.circe.generic.semiauto.deriveDecoder[AuditVersion]
}
