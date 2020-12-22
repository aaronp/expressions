package expressions.franz

import args4c.implicits.configAsRichConfig
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.streams.serdes.avro.GenericAvroSerde
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import zio.ZManaged
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.{ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde.Serde

import java.util.UUID
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.Try
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}

import scala.reflect.ClassTag

object FranzConfig {

  def apply(conf: String, theRest: String*): FranzConfig = new FranzConfig(asConfig(conf, theRest: _*))

  def fromRootConfig(rootConfig: Config = ConfigFactory.load()) = FranzConfig(rootConfig.getConfig("app.franz"))

  def stringKeyAvroValueConfig(rootFallbackConfig: Config = ConfigFactory.load()): FranzConfig = FranzConfig.fromRootConfig {
    keyConf[StringDeserializer, StringSerializer]
      .withFallback(valueConf[KafkaAvroDeserializer, KafkaAvroSerializer])
      .withFallback(rootFallbackConfig)
  }
  def avroKeyValueConfig(rootFallbackConfig: Config = ConfigFactory.load()): FranzConfig = FranzConfig.fromRootConfig {
    keyConf[KafkaAvroDeserializer, KafkaAvroSerializer]
      .withFallback(valueConf[KafkaAvroDeserializer, KafkaAvroSerializer])
      .withFallback(rootFallbackConfig)
  }

  def keyConf[D <: Deserializer[_]: ClassTag, S <: Serializer[_]: ClassTag]   = serdeConf[D, S]("key")
  def valueConf[D <: Deserializer[_]: ClassTag, S <: Serializer[_]: ClassTag] = serdeConf[D, S]("value")
  private def serdeConf[D <: Deserializer[_]: ClassTag, S <: Serializer[_]: ClassTag](`type`: String) = {
    ConfigFactory.parseString(
      s"""app.franz.kafka {
         |  ${`type`}.deserializer : "${implicitly[ClassTag[D]].runtimeClass.getName}"
         |  ${`type`}.serializer : "${implicitly[ClassTag[S]].runtimeClass.getName}"
         |}""".stripMargin,
      ConfigParseOptions.defaults.setOriginDescription("FranzConfig (programmatic)")
    )
  }

  def asConfig(conf: String, theRest: String*) = {
    import args4c.implicits._
    (conf +: theRest).toArray.asConfig().getConfig("app.franz")
  }

}
final case class FranzConfig(franzConfig: Config = ConfigFactory.load().getConfig("app.franz")) {

  override def toString: String = {
    import args4c.implicits._
    franzConfig
      .summaryEntries()
      .map { e =>
        s"app.franz.${e}"
      }
      .mkString("\n")
  }
  def withOverrides(conf: String, theRest: String*): FranzConfig = withOverrides(FranzConfig.asConfig(conf, theRest: _*))

  def withOverrides(newFranzConfig: Config): FranzConfig = {
    copy(franzConfig = newFranzConfig.withFallback(franzConfig).resolve())
  }
  def withOverrides(newFranzConfig: FranzConfig): FranzConfig = {
    copy(franzConfig = newFranzConfig.franzConfig.withFallback(franzConfig).resolve())
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

  //lazy val deserializer: Serde[Any, GenericRecord] = genericAvroSerdeFromConfig

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

  def producerSettings: ProducerSettings = {
    ProducerSettings(kafkaConfig.asList("brokers"))
      .withProperties(asJavaMap(kafkaConfig).asScala.toSeq: _*)
  }

  private lazy val genericAvroSerdeFromConfig: Serde[Any, GenericRecord] = {
    val serde                = new GenericAvroSerde(schemaRegistryClient)
    val isSerdeForRecordKeys = kafkaConfig.getBoolean("isForRecordKeys")
    serde.configure(asJavaMap(kafkaConfig), isSerdeForRecordKeys)
    Serde(serde)
  }

  def keySerde[K]   = serdeFor[K](kafkaConfig.getConfig("key"))
  def valueSerde[V] = serdeFor[V](kafkaConfig.getConfig("value"))

  def producer[K, V]: ZManaged[Any, Throwable, Producer.Service[Any, K, V]] = producer[K, V](keySerde[K], valueSerde[V])

  def producer[K, V](keySerde: Serde[Any, K], valueSerde: Serde[Any, V]): ZManaged[Any, Throwable, Producer.Service[Any, K, V]] = {
    Producer.make(producerSettings, keySerde, valueSerde)
  }

  /**
    * Convenience method for creating a producer which writes string keys and avro values to a topic
    * @return a producer which writes string keys and avro values to a topic
    */
  def stringAvroProducer: ZManaged[Any, Throwable, Producer.Service[Any, String, GenericRecord]] = producer(Serde.string, genericAvroSerdeFromConfig)

  private lazy val schemaRegistryClient: SchemaRegistryClient = {
    val baseUrls            = kafkaConfig.asList("schema.registry.url").asJava
    val identityMapCapacity = kafkaConfig.getInt("identityMapCapacity")
    new CachedSchemaRegistryClient(baseUrls, identityMapCapacity)
  }

  /**
    * The keys and values will have a 'serializer' and 'deserializer'
    * @param serdeConfig
    * @tparam A
    * @return
    */
  private def serdeFor[A](serdeConfig: Config): Serde[Any, A] = {
    val deserializerName = serdeConfig.getString("deserializer")

    deserializerName.toLowerCase match {
      case "string" | "strings" => Serde.string.asInstanceOf[Serde[Any, A]]
      case "long" | "longs"     => Serde.long.asInstanceOf[Serde[Any, A]]
      case "avro"               => genericAvroSerdeFromConfig.asInstanceOf[Serde[Any, A]]
      case _ =>
        val kafkaDeserializer: Deserializer[A] = instantiate[Deserializer[A]](serdeConfig.getString("deserializer"))
        val kafkaSerializer: Serializer[A]     = instantiate[Serializer[A]](serdeConfig.getString("serializer"))

        Serde[Any, A](zio.kafka.serde.Deserializer.apply[A](kafkaDeserializer))(zio.kafka.serde.Serializer(kafkaSerializer))
    }
  }

  private def instantiate[A](className: String): A = {
    val c1ass = Class.forName(className)
    // the schema registry is only one way to do Serde -- just try and instantiate the serde via
    // a known SchemaRegistryClient constructor -- otherwise fallback to just the no-args variant
    val schemaRegTry = Try {
      val constructor = c1ass.getConstructor(classOf[SchemaRegistryClient])
      constructor.newInstance(schemaRegistryClient).asInstanceOf[A]
    }
    schemaRegTry.getOrElse {
      c1ass.getConstructor().newInstance().asInstanceOf[A]
    }
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
