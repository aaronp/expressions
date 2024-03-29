package expressions.client.kafka

import io.circe.Json
import io.circe.Codec

case class RecordCoords(topic: String, offset: Long, partition: Int, key: String)

object RecordCoords {
  given codec : Codec[RecordCoords] = io.circe.generic.semiauto.deriveCodec[RecordCoords]
}

case class RecordSummary(record: RecordCoords, message: String, value: Json, timestampEpochMillis: Long, supplementaryData: Json = Json.Null)

object RecordSummary {
  given codec : Codec[RecordSummary] = io.circe.generic.semiauto.deriveCodec[RecordSummary]
}

case class ConsumerStats(id: String,
                         totalRecords: Long = 0L,
                         stdout :Seq[String] = Nil,
                         stderr :Seq[String] = Nil,
                         recentRecords: Seq[RecordSummary] = Nil,
                         errors: Seq[RecordSummary] = Nil) {
  def ++(batch: Seq[RecordSummary]): ConsumerStats = {
    copy(recentRecords = (batch ++ recentRecords).take(100), totalRecords = totalRecords + batch.size)
  }
}

object ConsumerStats {
  given codec : Codec[ConsumerStats] = io.circe.generic.semiauto.deriveCodec[ConsumerStats]
}
