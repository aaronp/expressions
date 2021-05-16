package expressions.rest.server

import args4c.StringEntry
import args4c.implicits.configAsRichConfig
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.Unquote
import expressions.franz.{FranzConfig, SupportedType}
import io.circe.{Json, Codec}
import zio.ZIO

/**
  * The parsed pieces from the typesafe config in a json-friendly data structure
  *
  * @param topic     the kafka topic
  * @param brokers   the kafka brokers
  * @param mappings  a mapping of the topic to
  * @param keyType   the key type for the consumer
  * @param valueType the value type for the consumer
  */
case class ConfigSummary(topic: String,
                         brokers: List[String],
                         mappings: Map[String, List[String]],
                         keyType: String,
                         valueType: String,
                         producerKeyType: String,
                         producerValueType: String) {

  def keySerde(keyTypeStr: String): (String, String) =
    SupportedType
      .serdeForName(keyTypeStr)
      .fold(keyTypeStr -> keyTypeStr)(serde =>
        serde.serializer().getClass.getName ->
          serde.deserializer().getClass.getName)

  def valueSerde(valueTypeStr: String): (String, String) =
    SupportedType
      .serdeForName(valueTypeStr)
      .fold(valueTypeStr -> valueTypeStr)(serde =>
        serde.serializer().getClass.getName ->
          serde.deserializer().getClass.getName)

  def asConfigJson(): Json = ConfigSummary.asJson(asConfig())

  /** @return the config summary as a config
    */
  def asConfig(): Config = {
    val consNamespace: String = {
      SupportedType
        .avroNamespaceForName(keyType)
        .orElse(SupportedType.avroNamespaceForName(valueType))
        .fold("") { ns =>
          s"""namespace : "$ns" """
        }
    }
    val prodNamespace: String = {
      SupportedType
        .avroNamespaceForName(producerKeyType)
        .orElse(SupportedType.avroNamespaceForName(producerValueType))
        .fold("") { ns =>
          s"""namespace : "$ns" """
        }
    }

    val mappingsEntries = mappings.map {
      case (k, path) => s""" "${Unquote(k)}" : ${path.mkString("\"", "/", "\"")} """
    }

    val (keySer, keyDe)     = keySerde(keyType)
    val (valueSer, valueDe) = valueSerde(valueType)

    val (prodKeySer, prodKeyDe)     = keySerde(producerKeyType)
    val (prodValueSer, prodValueDe) = valueSerde(producerValueType)

    val brokerList = if (brokers.nonEmpty) brokers.mkString("bootstrap.servers : \"", ",", "\"") else ""
    ConfigFactory.parseString(s"""
         |app.mapping {
         |${mappingsEntries.mkString("    ", "\n    ", "")}
         |}
         |
         |app.franz : {
         |  consumer : {
         |    topic : "${topic}"
         |    $brokerList
         |
         |    key.serializer: "${keySer}"
         |    key.deserializer: "${keyDe}"
         |
         |    value.serializer: "${valueSer}"
         |    value.deserializer: "${valueDe}"
         |
         |    $consNamespace
         |  }
         |  producer : {
         |    topic : "${topic}"
         |    $brokerList
         |    key.serializer: "${prodKeySer}"
         |    key.deserializer: "${prodKeyDe}"
         |
         |    value.serializer: "${prodValueSer}"
         |    value.deserializer: "${prodValueDe}"
         |
         |    $prodNamespace
         |  }
         |}""".stripMargin)
  }
}

object ConfigSummary {
  given codec : Codec[ConfigSummary] = io.circe.generic.semiauto.deriveCodec[ConfigSummary]

  def asJson(config: Config): Json = {
    val jasonStr = config.root().render(ConfigRenderOptions.concise())
    io.circe.parser.parse(jasonStr).toTry.get
  }

  def empty = ConfigSummary("", Nil, Map.empty, "", "", "", "")
  def fromRootConfig(rootConfig: Config): ConfigSummary = {
    val fc       = FranzConfig.fromRootConfig(rootConfig)
    val mappings = MappingConfig(rootConfig).mappings.toMap
    ConfigSummary(
      topic = fc.topic,
      brokers = fc.consumerSettings.bootstrapServers,
      mappings,
      fc.consumerKeyType.name,
      fc.consumerValueType.name,
      fc.producerKeyType.name,
      fc.producerValueType.name
    )
  }
}

case class ConfigLine(comments: List[String], origin: String, key: String, value: String)

object ConfigLine {
  given codec : Codec[ConfigLine]= io.circe.generic.semiauto.deriveCodec[ConfigLine]

  def apply(config: Config): Seq[ConfigLine] = {
    config.summaryEntries().map {
      case StringEntry(comments, origin, key, value) => ConfigLine(comments, origin, key, value)
    }
  }
}
