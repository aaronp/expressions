package expressions.rest.server

import com.typesafe.config.ConfigFactory
import expressions.client.kafka.PostRecord
import expressions.franz.{ForeachPublisher, FranzConfig, SupportedType}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio.URIO
import zio.blocking.Blocking

import scala.jdk.CollectionConverters._

object KafkaPublishService {

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
