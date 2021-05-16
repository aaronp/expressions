package expressions.rest.server.kafka

import com.typesafe.config.ConfigFactory
import expressions.client.kafka.PostRecord
import expressions.franz.{ForeachPublisher, FranzConfig, SupportedType}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio.URIO
import zio.blocking.Blocking
import zio.clock.Clock

import scala.jdk.CollectionConverters.*

/**
  * Handles the publishing of [[PostRecord]] records to Kafka
  */
object KafkaPublishService {
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  def apply(config: FranzConfig): PostRecord => URIO[Clock with Blocking, Int] = { record =>
    execPost(record, config)
  }

  def execPost(post: PostRecord, config: FranzConfig): URIO[Clock with Blocking, Int] = {
    val newConfig: FranzConfig = config.withOverrides(ConfigFactory.parseString(post.config))
    import args4c.implicits._
    logger.debug(s"Exec: \n${newConfig.franzConfig.summary()}\n")

    val kt           = newConfig.consumerKeyType
    val vt           = newConfig.consumerValueType
    val kafkaRecords = asRecords(kt, vt, post, newConfig)
    ForeachPublisher
      .publishAll(newConfig, kafkaRecords)
      .map(_.size)
      .tapEither {
        case Left(res)  => zio.UIO(logger.error(s"PUBLISH RETURNED $res", res))
        case Right(res) => zio.UIO(logger.debug(s"PUBLISH OK $res"))
      }
      .orDie
  }

  /**
    * convert the PostRecord into a collection of ProducerRecords
    */
  def asRecords[K, V](keyType: SupportedType[K], valueType: SupportedType[V], post: PostRecord, newConfig: FranzConfig): Seq[ProducerRecord[K, V]] = {

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

    logger.debug(s"Publishing $key of type ${value.getClass} to $topic:\n$value")

    post.partition match {
      case Some(p) => new ProducerRecord[K, V](topic, p, key, value, kafkaHeaders.asJava)
      case None    => new ProducerRecord[K, V](topic, null, key, value, kafkaHeaders.asJava)
    }
  }
}
