package expressions.rest

import expressions.RichDynamicJson
import expressions.template.Message
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, SchemaRegistryClient}
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import zio.Has
import scala.jdk.CollectionConverters._

package object server {

  type Disk      = Has[Disk.Service]
  type Analytics = Has[Analytics.Service]
  type Topic     = String

  type JsonMsg = Message[RichDynamicJson, RichDynamicJson]

  /**
    * pimped out the schema client
    * @param client
    */
  implicit class RichSchemaClient(val client: SchemaRegistryClient) extends AnyVal {
    def subjects: Iterable[String]             = client.getAllSubjects.asScala
    def versionsFor(subject: String): Set[Int] = client.getAllVersions(subject).asScala.map(_.intValue()).toSet
    def subjectVersions: Map[String, Set[Int]] =
      subjects.map { s =>
        s -> versionsFor(s)
      }.toMap
    def latestSchemasById: Map[String, Schema] = subjectVersions.map {
      case (s, versions) =>
        val got: Schema = client.getByVersion(s, versions.max, false)
        (s, got)
    }
    def diffById: Map[String, (SchemaMetadata, SchemaMetadata)] = subjectVersions.flatMap {
      case (s, versions) =>
        diffMetadata(s, versions).map { diff =>
          (s, diff)
        }
    }

    def compatibilityFor(subject: String) = client.getCompatibility(subject)
    def modeFor(subject: String)          = client.getCompatibility(subject)

    def diffSchema(subject: String): Option[(Schema, Schema)] = diffSchema(subject, versionsFor(subject))

    def diffSchema(subject: String, versions: Set[Int]): Option[(Schema, Schema)] = {
      versions match {
        case set if set.size > 1 =>
          val latest       = set.max
          val prev         = (set - latest).ensuring(_.size == set.size - 1).max
          val latestSchema = client.getByVersion(subject, latest, false)
          val prevSchema   = client.getByVersion(subject, prev, false)
          Some(prevSchema -> latestSchema)
        case _ => None
      }
    }

    def diffMetadata(subject: String): Option[(SchemaMetadata, SchemaMetadata)] = diffMetadata(subject, versionsFor(subject))

    def diffMetadata(subject: String, versions: Set[Int]) = {
      versions match {
        case set if set.size > 1 =>
          val latest       = set.max
          val prev         = (set - latest).ensuring(_.size == set.size - 1).max
          val latestSchema = schemaMetadataForVersion(subject, latest)
          val prevSchema   = schemaMetadataForVersion(subject, prev)
          Some(prevSchema -> latestSchema)
        case _ => None
      }
    }

    def metadataById: Map[String, SchemaMetadata] =
      subjects.map { s =>
        (s, schemaMetadata(s))
      }.toMap

    def mode                                                                    = client.getMode
    def schemaMetadata(subject: String): SchemaMetadata                         = client.getLatestSchemaMetadata(subject)
    def schemaMetadataForVersion(subject: String, version: Int): SchemaMetadata = client.getSchemaMetadata(subject, version)

    def describe: String = {
      val subjcts = subjectVersions.map {
        case (s, v) => v.toList.sorted.mkString(s"  ${s}: [", ",", "]")
      }

      s"""Schema Registry in mode ${mode} w/ ${subjcts.size} subjects:
         |${subjcts.mkString("\n\t", "\n\t", "\n\t")}""".stripMargin
    }
  }
}
