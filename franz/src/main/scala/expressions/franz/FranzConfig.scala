package expressions.franz

import args4c.implicits.configAsRichConfig
import com.typesafe.config.{Config, ConfigFactory}

class FranzConfig(appConfig: Config = ConfigFactory.load()) {
  val kafkaConfig    = appConfig.getConfig("kafka")
  val topic          = appConfig.getString("kafka.topic")
  val blockOnCommits = appConfig.getBoolean("kafka.blockOnCommits")
  val concurrency = appConfig.getInt("kafka.concurrency") match {
    case n if n <= 0 => java.lang.Runtime.getRuntime.availableProcessors()
    case n           => n
  }

  lazy val consumerSettings = ConsumerSettingsFromConfig(kafkaConfig)

  lazy val deserializer = AvroSerde.generic {
    appConfig.getConfig("schemas")
  }.deserializer

  val batchSize   = appConfig.getInt("batchWindow.maxCount")
  val batchWindow = appConfig.asFiniteDuration("batchWindow.maxTime")
}
