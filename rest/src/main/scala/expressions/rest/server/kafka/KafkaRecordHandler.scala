package expressions.rest.server.kafka

import expressions.CodeTemplate.Expression
import expressions.rest.server.{JsonMsg, Topic}
import expressions.template.Context
import zio.kafka.consumer.CommittableRecord

import scala.util.Try

/**
  * @param transformForTopic a function which can lookup a context transformation for a given topic
  * @param asContext         a function which can produce a 'Context' for a Kafka record
  * @tparam B
  */
final case class KafkaRecordHandler[B](transformForTopic: Topic => Try[Expression[JsonMsg, B]], asContext: CommittableRecord[_, _] => Context[JsonMsg])
