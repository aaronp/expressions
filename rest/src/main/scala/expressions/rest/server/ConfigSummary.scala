package expressions.rest.server

import args4c.StringEntry
import com.typesafe.config.Config


/**
  * The parsed pieces from the typesafe config in a json-friendly data structure
  *
  * @param topic
  * @param brokers
  * @param mappings a mapping of the topic to
  * @param keyType
  * @param valueType
  */
case class ConfigSummary(topic: String, brokers: List[String], mappings: Map[String, List[String]], keyType: String, valueType: String)
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