package expressions.client.kafka

import io.circe.Json

case class RecordCoords(topic: String, offset: Long, partition: Int, key: String)

object RecordCoords {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[RecordCoords]
}

case class RecordSummary(record: RecordCoords, message: String, value: Json, timestampEpochMillis: Long, supplementaryData: Json = Json.Null)

object RecordSummary {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[RecordSummary]
}

case class ConsumerStats(id: String, totalRecords: Long = 0L, recentRecords: Seq[RecordSummary] = Nil, errors: Seq[RecordSummary] = Nil) {
  def ++(batch: Seq[RecordSummary]): ConsumerStats = {
    copy(recentRecords = (batch ++ recentRecords).take(100), totalRecords = totalRecords + batch.size)
  }
}

object ConsumerStats {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[ConsumerStats]
}