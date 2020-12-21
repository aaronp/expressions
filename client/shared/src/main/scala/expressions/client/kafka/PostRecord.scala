package expressions.client.kafka

import expressions.client.kafka.PostRecord.Placeholder
import io.circe.Json

/**
  * A means for us to publish data to a topic
  * @param config
  * @param partition
  * @param topicOverride
  * @param key
  * @param data
  */
case class PostRecord(config: String,
                      data: Json,
                      key: Json = Json.fromString("key"),
                      repeat: Int = 0,
                      partition: Option[Int] = None,
                      topicOverride: Option[String] = None,
                      headers: Map[String, String] = Map.empty) {
  def isTombstone                                = data == null || data.asString.exists(_.trim.isEmpty) || data.isNull
  def replacePlaceholder(value: Int): PostRecord = replacePlaceholder(value.toString)
  def replacePlaceholder(value: String): PostRecord = {
    copy(
      config = config.replace(Placeholder, value),
      data = PostRecord.replaceAll(data, Placeholder, value),
      key = PostRecord.replaceAll(key, Placeholder, value),
      topicOverride = topicOverride.map(_.replace(Placeholder, value)),
      headers = headers.map {
        case (k, v) =>
          k.replace(Placeholder, value) -> v.replace(Placeholder, value)
      }
    )
  }
}

object PostRecord {
  def replaceAll(json: Json, key: String, value: String): Json =
    json.fold(
      Json.Null,
      Json.fromBoolean,
      Json.fromJsonNumber,
      str => Json.fromString(str.replace(key, value)),
      array => Json.fromValues(array.map(replaceAll(_, key, value))),
      obj => Json.fromFields {
        obj.toMap.map {
          case (k, v) =>
            k.replace(Placeholder, value) -> replaceAll(v, key, value)
        }
      }
    )

  val Placeholder    = "{{i}}"
  implicit val codec = io.circe.generic.semiauto.deriveCodec[PostRecord]
}
