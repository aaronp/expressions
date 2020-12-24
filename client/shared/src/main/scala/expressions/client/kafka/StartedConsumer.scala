package expressions.client.kafka

/**
  * Keep track of our running consumers
  * @param id
  * @param config
  * @param startedAtEpoch
  */
case class StartedConsumer(id: String, config: String, startedAtEpoch: Long)

object StartedConsumer {
  implicit val codec = io.circe.generic.semiauto.deriveCodec[StartedConsumer]
}
