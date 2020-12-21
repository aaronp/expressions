package expressions.rest.server

import com.typesafe.config.ConfigFactory
import expressions.client.kafka.PostRecord
import expressions.franz.{ForeachPublisher, FranzConfig}
import org.apache.avro.specific.SpecificRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import zio.UIO

import scala.jdk.CollectionConverters._

object KafkaPublishService {

  def apply(config : FranzConfig) : PostRecord => UIO[Int] = {
    (record: PostRecord) => {

      val kafkaReords = asRecords(record)
      execPost(record, config)
    }
  }

  def execPost(post: PostRecord, config : FranzConfig) = {
    val newConfig: FranzConfig = config.withOverrides(ConfigFactory.parseString(post.config))
    val kafkaRecords = asRecords(post, newConfig)
    ForeachPublisher.publishAll(newConfig, )
    ???
  }

  def key(postRecord: PostRecord) = postRecord.key.noSpaces
  def value(postRecord: PostRecord) = if (postRecord.isTombstone) {
    null
  } else {
    TestData.fromJson[SpecificRecord](postRecord.data)
  }

  def asRecord(post: PostRecord, newConfig: FranzConfig): ProducerRecord[String, SpecificRecord] = {
    val kafkaHeaders: Iterable[Header] = post.headers.toList.map {
      case (key, value) => new RecordHeader(key, value.getBytes("UTF-8"))
    }
    val topic = post.topicOverride.getOrElse(newConfig.topic.replace("*", "any"))
    post.partition match {
      case Some(p) => new ProducerRecord(topic, p, key(post), value(post), kafkaHeaders.asJava)
      case None => new ProducerRecord(topic, null, key(post), value(post), kafkaHeaders.asJava)
    }
  }


  def asRecords(post: PostRecord, newConfig: FranzConfig) : Iterable[ProducerRecord[String, SpecificRecord]] = {
    post.repeat match {
      case n if n < 1 =>
        (0 until n).map(post.replacePlaceholder).map(asRecord(_, newConfig))
      case _          =>
        asRecord(post, newConfig) :: Nil
    }
  }
}
