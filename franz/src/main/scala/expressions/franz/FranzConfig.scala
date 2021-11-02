package expressions.franz

import args4c.implicits.configAsRichConfig
import com.typesafe.config.{Config, ConfigFactory, ConfigParseOptions}
import eie.io.AlphaCounter
import io.confluent.kafka.schemaregistry.client.{CachedSchemaRegistryClient, SchemaRegistryClient}
import io.confluent.kafka.serializers.{KafkaAvroDeserializer, KafkaAvroSerializer}
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.*
import org.slf4j.LoggerFactory
import zio.blocking.Blocking
import zio.kafka.consumer.Consumer.{AutoOffsetStrategy, OffsetRetrieval}
import zio.kafka.consumer.{ConsumerSettings, Subscription}
import zio.kafka.producer.{Producer, ProducerSettings}
import zio.kafka.serde
import zio.kafka.serde.Serde
import zio.{RIO, RManaged, Task, ZIO, ZManaged}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object FranzConfig {

  def apply(conf: String, theRest: String*): FranzConfig = new FranzConfig(asConfig(conf, theRest: _*))

  def fromRootConfig(rootConfig: Config = ConfigFactory.load()): FranzConfig = FranzConfig(rootConfig.getConfig("app.franz"))

  def stringKeyAvroValueConfig(rootFallbackConfig: Config = ConfigFactory.load()): FranzConfig = FranzConfig.fromRootConfig {
    keyConf[StringDeserializer, StringSerializer]()
      .withFallback(valueConf[KafkaAvroDeserializer, KafkaAvroSerializer]())
      .withFallback(rootFallbackConfig)
  }

  def stringKeyStringValueConfig(rootFallbackConfig: Config = ConfigFactory.load()): FranzConfig = FranzConfig.fromRootConfig {
    keyConf[StringDeserializer, StringSerializer]()
      .withFallback(valueConf[StringDeserializer, StringSerializer]())
      .withFallback(rootFallbackConfig)
  }

  def avroKeyValueConfig(rootFallbackConfig: Config = ConfigFactory.load()): FranzConfig = FranzConfig.fromRootConfig {
    keyConf[KafkaAvroDeserializer, KafkaAvroSerializer]()
      .withFallback(valueConf[KafkaAvroDeserializer, KafkaAvroSerializer]())
      .withFallback(rootFallbackConfig)
  }

  def keyConf[D <: Deserializer[_]: ClassTag, S <: Serializer[_]: ClassTag]() = {
    serdeConf[D, S]("key")
  }

  def valueConf[D <: Deserializer[_]: ClassTag, S <: Serializer[_]: ClassTag]() = {
    serdeConf[D, S](s"value")
  }

  private def serdeConf[D <: Deserializer[_]: ClassTag, S <: Serializer[_]: ClassTag](`type`: String) = {
    ConfigFactory.parseString(
      s"""app.franz {
         |  consumer.${`type`}.deserializer : "${implicitly[ClassTag[D]].runtimeClass.getName}"
         |  consumer.${`type`}.serializer : "${implicitly[ClassTag[S]].runtimeClass.getName}" 
         |  producer.${`type`}.deserializer : "${implicitly[ClassTag[D]].runtimeClass.getName}"
         |  producer.${`type`}.serializer : "${implicitly[ClassTag[S]].runtimeClass.getName}"
         |}""".stripMargin,
      ConfigParseOptions.defaults.setOriginDescription("FranzConfig (programmatic)")
    )
  }

  def asConfig(conf: String, theRest: String*) = {
    import args4c.implicits.*
    (conf +: theRest).toArray.asConfig().getConfig("app.franz")
  }

  private val counter = AlphaCounter.from(System.currentTimeMillis())

  def nextRand() = counter.next()

  @tailrec
  def unquote(s: String): String = s.trim match {
    case s""""${str}"""" => unquote(str)
    case str             => str
  }
}

final case class FranzConfig(franzConfig: Config = ConfigFactory.load().getConfig("app.franz")) {
  override def toString: String = {
    import args4c.implicits.*
    franzConfig
      .summaryEntries()
      .map { e =>
        s"app.franz.${e}"
      }
      .mkString("\n")
  }

  def defaultSeed = System.currentTimeMillis()

  def withOverrides(conf: String, theRest: String*): FranzConfig = withOverrides(FranzConfig.asConfig(conf, theRest: _*))

  def withOverrides(newConfig: Config): FranzConfig = {
    val newFranzConfig = if (newConfig.hasPath("app.franz")) {
      newConfig.getConfig("app.franz")
    } else {
      newConfig
    }
    val updated = newFranzConfig.withFallback(franzConfig).resolve()
    copy(franzConfig = updated)
  }

  def withOverrides(newFranzConfig: FranzConfig): FranzConfig = {
    copy(franzConfig = newFranzConfig.franzConfig.withFallback(franzConfig).resolve())
  }

  val consumerConfig = franzConfig.getConfig("consumer")
  val producerConfig = franzConfig.getConfig("producer")

  private lazy val randomTopic = s"topic${rand()}"
  private lazy val randomGroup = s"group${rand()}"
  val topic = consumerConfig.getString("topic") match {
    case "<random>" => randomTopic
    case topic      => topic
  }
  val producerTopic = producerConfig.getString("topic") match {
    case "<random>" => randomTopic
    case topic      => topic
  }
  lazy val subscription: Subscription = topic match {
    case topic if topic.contains("*") => Subscription.pattern(topic.r)
    case topic if topic.contains(",") => Subscription.Topics(topic.split(",", -1).toSet)
    case topic                        => Subscription.topics(topic)
  }
  val blockOnCommits = consumerConfig.getBoolean("blockOnCommits")
  val concurrency = consumerConfig.getInt("concurrency") match {
    case n if n <= 0 => java.lang.Runtime.getRuntime.availableProcessors()
    case n           => n
  }

  val batchSize   = franzConfig.getInt("batchWindow.maxCount")
  val batchWindow = franzConfig.asFiniteDuration("batchWindow.maxTime")

  def groupId(kafkaConfig: Config) = kafkaConfig.getString("groupId") match {
    case "<random>" => randomGroup
    case id         => id
  }

  lazy val consumerSettings: ConsumerSettings = {
    val offset = consumerConfig.getString("offset") match {
      case "earliest" => OffsetRetrieval.Auto(AutoOffsetStrategy.Earliest)
      case "latest"   => OffsetRetrieval.Auto(AutoOffsetStrategy.Latest)
      case specific =>
        sys.error(s"Bad kafka.offset: only earliest/latest currently supported: $specific")
    }

    ConsumerSettings(consumerConfig.asList("bootstrap.servers"))
      .withProperties(asJavaMap(consumerConfig).asScala.toSeq: _*)
      .withGroupId(groupId(consumerConfig))
      .withOffsetRetrieval(offset)
  }

  def producerSettings: ProducerSettings = {
    val map = asJavaMap(producerConfig).asScala
    ProducerSettings(producerConfig.asList("bootstrap.servers"))
      .withProperties(map.toSeq: _*)
  }

  def consumerKeyType: SupportedType[_] = keyType(consumerConfig.getConfig("key"))

  def consumerKeySerde[K]: Task[Serde[Any, K]] = keySerde[K]()

  def consumerValueType: SupportedType[_] = valueType(consumerConfig.getConfig("value"))

  def consumerValueSerde[V]: Task[Serde[Any, V]] = valueSerde[V]()

  def producerKeyType: SupportedType[_] = typeOf(producerConfig.getConfig("key"), producerNamespace)

  def producerKeySerde[K]: Task[Serde[Any, K]] = keySerde[K]()

  def producerValueType: SupportedType[_] = typeOf(producerConfig.getConfig("value"), producerNamespace)

  def producerValueSerde[V]: Task[Serde[Any, V]] = valueSerde[V]()

  def keyType(keyConfig: Config = consumerConfig.getConfig("key")): SupportedType[_] = typeOf(keyConfig, consumerNamespace)

  def keySerde[K](keyConfig: Config = consumerConfig.getConfig("key")): Task[Serde[Any, K]] = serdeFor[K](keyConfig, true)

  def valueType(valueConfig: Config = consumerConfig.getConfig("value")): SupportedType[_] = typeOf(valueConfig, consumerNamespace)

  def valueSerde[V](valueConfig: Config = consumerConfig.getConfig("value")): Task[Serde[Any, V]] = serdeFor[V](valueConfig, false)

  def producer: RManaged[Blocking, Producer] = Producer.make(producerSettings)

  private def baseUrls = consumerConfig.asList("schema.registry.url").asJava

  lazy val schemaRegistryClient: SchemaRegistryClient = {
    val identityMapCapacity = consumerConfig.getInt("identityMapCapacity")
    new CachedSchemaRegistryClient(baseUrls, identityMapCapacity)
  }

  /**
    * The keys and values will have a 'serializer' and 'deserializer'
    *
    * @param serdeConfig
    * @tparam A
    * @return
    */
  private def serdeFor[A](serdeConfig: Config, isKey: Boolean): Task[Serde[Any, A]] =
    SerdeParser(this).serdeFor[A](serdeConfig, isKey)

  def consumerNamespace = franzConfig.getString("consumer.namespace") match {
    case "<random>" => randomValue
    case name       => name
  }

  def producerNamespace = franzConfig.getString("producer.namespace") match {
    case "<random>" => randomValue
    case name       => name
  }

  def typeOf(serdeConfig: Config, defaultAvroNamespace: => String): SupportedType[_] = {
    val serializerName = serdeConfig.getString("serializer")
    serializerName.toLowerCase match {
      case ""                     => SupportedType.STRING
      case "string" | "strings"   => SupportedType.STRING
      case "long" | "longs"       => SupportedType.LONG
      case "bytes" | "byte array" => SupportedType.BYTE_ARRAY
      case "avro"                 => SupportedType.RECORD(defaultAvroNamespace)
      case s"avro:$ns"            => SupportedType.RECORD(ns)
      case _ =>
        instantiate[Any](serializerName) match {
          case _: StringSerializer                                             => SupportedType.STRING
          case _: ByteArraySerializer                                          => SupportedType.BYTE_ARRAY
          case _: ByteBufferSerializer                                         => SupportedType.BYTE_ARRAY
          case _: LongSerializer                                               => SupportedType.LONG
          case _: ByteArraySerializer                                          => SupportedType.BYTE_ARRAY
          case _: io.confluent.kafka.serializers.KafkaAvroSerializer           => SupportedType.RECORD(defaultAvroNamespace)
          case _: io.confluent.kafka.streams.serdes.avro.GenericAvroSerializer => SupportedType.RECORD(defaultAvroNamespace)
          case other                                                           => sys.error(s"Couldn't determine supported type from serializer '$other'")
        }
    }
  }

  def instantiate[A](className: String): A = {
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

  private def rand() = FranzConfig.nextRand()

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
