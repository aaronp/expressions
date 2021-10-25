package expressions.client.kafka

import io.circe.Codec

/**
  * Keep track of our running consumers
  * @param id
  * @param config
  * @param startedAtEpoch
  */
case class StartedConsumer(id: String, config: String, startedAtEpoch: Long)

object StartedConsumer {
  given codec : Codec[StartedConsumer] = io.circe.generic.semiauto.deriveCodec[StartedConsumer]
}
