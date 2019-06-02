package pipelines.reactive

import monix.reactive.{Consumer, Observer}

trait LowPriorityDataSinkImplicits {
  import scala.reflect.runtime.universe._
  implicit def asRichObserver[In:TypeTag](obs: Observer[In]): Consumer[In, Unit] = {
    Consumer.fromObserver[In](_ => obs)
  }
  implicit def asRichConsumer[In:TypeTag, Out](consumer: Consumer[In, Out]): ConsumerSyntax[In, Out] = {
    new ConsumerSyntax[In, Out](consumer, ContentType.of[In])
  }

}
