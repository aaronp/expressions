package pipelines.reactive

import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable

import scala.concurrent.Future

/**
  * Represents a data sink.
  *
  * There may be only one - or at least a fixed set, as we can use transforms to represent data pipelines e.g. through
  * a socket, and thus the actual sink itself can just be something which audits/records the fact that a pipeline has been run.
  */
sealed trait DataSink {
  type Result

  def connect(observable: Observable[_])(implicit scheduler: Scheduler): Result
}

object DataSink {

  def apply() = new DataSink {
    type Result = Future[Long]
    override def connect(observable: Observable[_])(implicit scheduler: Scheduler): CancelableFuture[Long] = {
      observable.countL.runToFuture
    }
  }
}
