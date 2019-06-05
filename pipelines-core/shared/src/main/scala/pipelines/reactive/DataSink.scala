package pipelines.reactive

import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.{Consumer, Observable}

/**
  * Represents a data sink.
  *
  * There may be only one - or at least a fixed set, as we can use transforms to represent data pipelines e.g. through
  * a socket, and thus the actual sink itself can just be something which audits/records the fact that a pipeline has been run.
  */
sealed trait DataSink {
  type T <: DataSink
  type Input
  type Output

  final def addMetadata(key: String, value: String): T = addMetadata(Map(key -> value))

  def contentType: ContentType

  def addMetadata(entries: Map[String, String]): T

  def metadata: Map[String, String]
  def connect(observable: Observable[Input])(implicit scheduler: Scheduler): CancelableFuture[Output]
}

object DataSink {

  type Aux[A] = DataSink {
    type Output = A
  }

  import scala.reflect.runtime.universe._
  object syntax extends LowPriorityDataSinkImplicits

  case class Instance[In, Out](consumer: Consumer[In, Out], override val metadata: Map[String, String], override val contentType: ContentType) extends DataSink {
    override type T      = Instance[In, Out]
    override type Input  = In
    override type Output = Out
    override def connect(observable: Observable[Input])(implicit scheduler: Scheduler): CancelableFuture[Out] = {
      val result: Task[Out] = consumer(observable)
      result.runToFuture(scheduler)
    }

    override def addMetadata(entries: Map[String, String]): Instance[In, Out] = {
      copy(metadata = metadata ++ entries)
    }
  }

  def apply[In: TypeTag, Out](consumer: Consumer[In, Out], metadata: Map[String, String] = Map.empty): Instance[In, Out] = {
    apply(consumer, metadata, ContentType.of[In])
  }

  def apply[In, Out](consumer: Consumer[In, Out], metadata: Map[String, String], contentType: ContentType): Instance[In, Out] = {
    new Instance(consumer, metadata, contentType)
  }
}
