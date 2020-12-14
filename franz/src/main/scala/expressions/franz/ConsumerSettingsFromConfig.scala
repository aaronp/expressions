package expressions.franz

import args4c.implicits._
import com.typesafe.config.Config
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.ConsumerSettings

import java.util.UUID

/** A function which translates between a typesafe 'app'
  */
object ConsumerSettingsFromConfig {

  def apply(kafkaConfig: Config): ConsumerSettings = {
    val settings = {
      val groupId = kafkaConfig.getString("groupId") match {
        case "<random>" => UUID.randomUUID().toString.filter(_.isLetter)
        case id         => id
      }

      val offset = kafkaConfig.getString("offset") match {
        case "earliest" => OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest)
        case "latest"   => OffsetRetrieval.Auto(AutoOffsetStrategy.Latest)
        case specific =>
          sys.error(s"Bad kafka.offset: only earliest/latest currently supported: $specific")
      }

      ConsumerSettings(kafkaConfig.asList("brokers"))
        .withGroupId(groupId)
        .withOffsetRetrieval(offset)
    }
    settings
  }

}
