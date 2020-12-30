package expressions.rest.server

import com.typesafe.config.ConfigFactory
import expressions.client.kafka.PostRecord
import expressions.franz.{ForeachPublisher, FranzConfig, SupportedType}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.rest.entities.Schema
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, SchemaRegistryClient}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio.URIO
import zio.blocking.Blocking

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

object KafkaPublishService {

  implicit class RichClient(val client: SchemaRegistryClient) extends AnyVal {
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
    def modeFor(subject: String) = client.getCompatibility(subject)

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

  def apply(config: FranzConfig): PostRecord => URIO[Blocking, Int] = { record =>
    execPost(record, config)
  }

  def execPost(post: PostRecord, config: FranzConfig): URIO[Any with Blocking, Int] = {
    val newConfig: FranzConfig = config.withOverrides(ConfigFactory.parseString(post.config))

    val kt           = config.keyType
    val vt           = config.valueType
    val kafkaRecords = asRecords(kt, vt, post, config)
    ForeachPublisher.publishAll(newConfig, kafkaRecords).map(_.size).orDie
  }

  def asRecords[K, V](keyType: SupportedType[K], valueType: SupportedType[V], post: PostRecord, newConfig: FranzConfig) = {
    post.repeat match {
      case n if n > 1 =>
        (0 until n).map(post.replacePlaceholder).map(asRecord(keyType, valueType, _, newConfig))
      case _ =>
        asRecord(keyType, valueType, post.replacePlaceholder(0), newConfig) :: Nil
    }
  }

  def asRecord[K, V](keyType: SupportedType[K], valueType: SupportedType[V], post: PostRecord, newConfig: FranzConfig): ProducerRecord[K, V] = {
    val kafkaHeaders: Iterable[Header] = post.headers.toList.map {
      case (key, value) => new RecordHeader(key, value.getBytes("UTF-8"))
    }
    val topic = post.topicOverride.getOrElse(newConfig.topic.replace("*", "any"))

    val key = keyType.of(post.key)
    val value: V = if (post.isTombstone) {
      null.asInstanceOf[V]
    } else {
      valueType.of(post.data)
    }

    post.partition match {
      case Some(p) => new ProducerRecord[K, V](topic, p, key, value, kafkaHeaders.asJava)
      case None    => new ProducerRecord[K, V](topic, null, key, value, kafkaHeaders.asJava)
    }
  }
}
