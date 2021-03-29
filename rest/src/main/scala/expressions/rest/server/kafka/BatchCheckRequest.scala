package expressions.rest.server.kafka

import expressions.DynamicJson
import expressions.template.Message

/**
  * The user input
  */
final case class BatchCheckRequest(rootConfig: String, batch: Seq[Message[DynamicJson, DynamicJson]], script: String) {
  def asBatchInputs(context: BatchContext): Seq[BatchInput] = {
    batch
      .groupBy(_.topic)
      .map {
        case (topic, batch) => BatchInput(Batch(topic, batch.toVector), context)
      }
      .toSeq
  }
}
object BatchCheckRequest {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[BatchCheckRequest]
}
