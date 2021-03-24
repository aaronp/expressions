package expressions.rest.server

import args4c.StringEntry
import com.typesafe.config.{Config, ConfigFactory}
import expressions.franz.SupportedType

/**
  * The parsed pieces from the typesafe config in a json-friendly data structure
  *
  * @param topic the kafka topic
  * @param brokers the kafka brokers
  * @param mappings a mapping of the topic to
  * @param keyType the key type
  * @param valueType the value type
  */
case class ConfigSummary(topic: String,
                         brokers: List[String],
                         mappings: Map[String, List[String]],
                         keyType: String,
                         valueType: String,
                         producerKeyType: String,
                         producerValueType: String) {

  /** @return the configsummary as a config
    */
  def asConfig(): Config = {
    val namespaceSetting: String = {
      SupportedType
        .avroNamespaceForName(keyType)
        .orElse(SupportedType.avroNamespaceForName(valueType))
        .fold("") { ns =>
          s"""namespace : "$ns" """
        }
    }

    val mappingsEntries = mappings.map {
      case (k, path) => s""" "$k" : ${path.mkString("\"", "/", "\"")} """
    }

    val keySerde: (String, String) = SupportedType
      .serdeForName(keyType)
      .fold(keyType -> keyType)(serde =>
        serde.serializer().getClass.getName ->
          serde.deserializer().getClass.getName)
    val valueSerde: (String, String) = SupportedType
      .serdeForName(valueType)
      .fold(valueType -> valueType)(serde =>
        serde.serializer().getClass.getName ->
          serde.deserializer().getClass.getName)

    ConfigFactory.parseString(s"""app.franz : {
        |  consumer : {
        |    topic : "${topic}"
        |    ${if (brokers.nonEmpty) brokers.mkString("bootstrap.servers : \"", ",", "\"") else ""}
        |
        |    key.serializer: "${keySerde._1}"
        |    key.deserializer: "${keySerde._2}"
        |
        |    value.serializer: "${valueSerde._1}"
        |    value.deserializer: "${valueSerde._2}"
        |  }
        | $namespaceSetting
        |  mapping {
        |${mappingsEntries.mkString("    ", "\n    ", "")}
        |  }
        |}""".stripMargin)
  }
}
object ConfigSummary {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ConfigSummary]
}

case class ConfigLine(comments: List[String], origin: String, key: String, value: String)
object ConfigLine {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ConfigLine]
  def apply(config: Config): Seq[ConfigLine] = {
    import args4c.implicits._
    config.summaryEntries().map {
      case StringEntry(comments, origin, key, value) => ConfigLine(comments, origin, key, value)
    }
  }
}
