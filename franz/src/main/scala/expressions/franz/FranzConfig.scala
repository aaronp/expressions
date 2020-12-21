package expressions.franz

import args4c.implicits.configAsRichConfig
import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import org.apache.avro.generic.GenericRecord
import zio.ZManaged
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.{ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde

import java.util.UUID
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

object FranzConfig {
  def apply(conf: String, theRest: String*): FranzConfig = {
    import args4c.implicits._
    new FranzConfig((conf +: theRest).toArray.asConfig().getConfig("app.franz"))
  }
  def fromRootConfig(rootConfig: Config = ConfigFactory.load()) = FranzConfig(rootConfig.getConfig("app.franz"))

}
final case class FranzConfig(franzConfig: Config = ConfigFactory.load().getConfig("app.franz")) {

  def withOverrides(newFranzConfig: Config): FranzConfig = {
    copy(franzConfig = newFranzConfig.withFallback(franzConfig).resolve())
  }

  val kafkaConfig = franzConfig.getConfig("kafka")

  lazy val randomTopic = UUID.randomUUID().toString.filter(_.isLetter)
  lazy val randomGroup = UUID.randomUUID().toString.filter(_.isLetter)
  val topic = franzConfig.getString("kafka.topic") match {
    case "<random>" => randomTopic
    case topic      => topic
  }
  lazy val subscription = topic match {
    case topic if topic.contains("*") => Subscription.pattern(topic.r)
    case topic if topic.contains(",") => Subscription.Topics(topic.split(",", -1).toSet)
    case topic                        => Subscription.topics(topic)
  }
  val blockOnCommits = franzConfig.getBoolean("kafka.blockOnCommits")
  val concurrency = franzConfig.getInt("kafka.concurrency") match {
    case n if n <= 0 => java.lang.Runtime.getRuntime.availableProcessors()
    case n           => n
  }

  lazy val deserializer: Serde[Any, GenericRecord] = genericAvroSerdeFromConfig

  val batchSize   = franzConfig.getInt("batchWindow.maxCount")
  val batchWindow = franzConfig.asFiniteDuration("batchWindow.maxTime")

  def groupId(kafkaConfig: Config) = kafkaConfig.getString("groupId") match {
    case "<random>" => randomGroup
    case id         => id
  }

  lazy val consumerSettings: ConsumerSettings = {
    val offset = kafkaConfig.getString("offset") match {
      case "earliest" => OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest)
      case "latest"   => OffsetRetrieval.Auto(AutoOffsetStrategy.Latest)
      case specific =>
        sys.error(s"Bad kafka.offset: only earliest/latest currently supported: $specific")
    }

    ConsumerSettings(kafkaConfig.asList("brokers"))
      .withProperties(asJavaMap(kafkaConfig).asScala.toSeq: _*)
      .withGroupId(groupId(kafkaConfig))
      .withOffsetRetrieval(offset)
  }

  lazy val genericAvroSerdeFromConfig: Serde[Any, GenericRecord] = {
    val serde                = new GenericAvroSerde()
    val isSerdeForRecordKeys = kafkaConfig.getBoolean("isForRecordKeys")
    serde.configure(asJavaMap(kafkaConfig), isSerdeForRecordKeys)
    Serde(serde)
  }

  def producer[K, V]: ZManaged[Any, Throwable, Producer.Service[Any, K, V]] = {
    val keySerde   = serdeFor[K](franzConfig.getString("serde.keys"))
    val valueSerde = serdeFor[V](franzConfig.getString("serde.values"))
    producer[K, V](keySerde, valueSerde)
  }

  def producer[K, V](keySerde: Serde[Any, K], valueSerde: Serde[Any, V]): ZManaged[Any, Throwable, Producer.Service[Any, K, V]] = {
    Producer.make(producerSettings, keySerde, valueSerde)
  }

  def stringAvroProducer: ZManaged[Any, Throwable, Producer.Service[Any, String, GenericRecord]] = producer(Serde.string, genericAvroSerdeFromConfig)

  private def serdeFor[A](name: String): Serde[Any, A] = name.toLowerCase match {
    case "string" | "strings" => Serde.string.asInstanceOf[Serde[Any, A]]
    case "long" | "longs"     => Serde.long.asInstanceOf[Serde[Any, A]]
    case "avro"               => genericAvroSerdeFromConfig.asInstanceOf[Serde[Any, A]]
    case other                =>
      //franzConfig.getString("keySerde")
      sys.error(s"TODO: plug in generic serde support for '$other'. Currently supported: string, long, avro")
  }

  def producerSettings: ProducerSettings = {
    ProducerSettings(kafkaConfig.asList("brokers"))
      .withProperties(asJavaMap(kafkaConfig).asScala.toSeq: _*)
  }

  def asJavaMap(config: Config): java.util.Map[String, String] = {
    val jMap = new java.util.HashMap[String, String]()
    config.entries().foreach {
      case (key, value) => jMap.put(key, valueOf(key, value.render()))
    }
    jMap
  }

  private def rand()   = UUID.randomUUID().toString.filter(_.isLetter)
  private val UnquoteR = """ *"(.*)" *""".r

  private lazy val randomValue = rand().toLowerCase()
  @tailrec
  private def valueOf(key: String, value: String): String = value match {
    case UnquoteR(x) => valueOf(key, x)
    case "<random>" =>
      key match {
        case "topic"   => randomTopic
        case "groupId" => randomGroup
        case _         => s"$key-$randomValue"
      }
    case x => x
  }
}
