package expressions.client.kafka

case class StartedConsumer(id: String, config: String, startedAtEpoch: Long)

object StartedConsumer {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[StartedConsumer]
}
