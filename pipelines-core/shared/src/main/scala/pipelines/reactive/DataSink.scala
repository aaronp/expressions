package pipelines.reactive

import monix.eval.Task
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.subjects.Var
import monix.reactive.{Consumer, Observable}

/**
  * Represents a data sink.
  *
  * There may be only one - or at least a fixed set, as we can use transforms to represent data pipelines e.g. through
  * a socket, and thus the actual sink itself can just be something which audits/records the fact that a pipeline has been run.
  */
trait DataSink extends HasMetadata {
  type T <: DataSink
  type Input
  type Output

  /** @return this instance as a parameterized type, just to help the compiler when creating Pipelines
    */
  def aux: DataSink.Aux[Input, Output] = this

  final def addMetadata(key: String, value: String): T = addMetadata(Map(key -> value))

  def inputType: ContentType

  def addMetadata(entries: Map[String, String]): T

  def connect(contentType: ContentType, observable: Observable[Input], sourceMetadata : Map[String, String])(implicit scheduler: Scheduler): CancelableFuture[Output]
}

object DataSink {

  type Aux[In, Out] = DataSink {
    type Input  = In
    type Output = Out
  }

  import scala.reflect.runtime.universe._
  object syntax extends LowPriorityDataSinkImplicits

  case class Instance[In, Out](consumer: Consumer[In, Out], override val metadata: Map[String, String], override val inputType: ContentType) extends DataSink {
    override type T      = Instance[In, Out]
    override type Input  = In
    override type Output = Out
    override def connect(contentType: ContentType, observable: Observable[Input], sourceMetadata : Map[String, String])(implicit scheduler: Scheduler): CancelableFuture[Out] = {
      val result: Task[Out] = consumer(observable)
      result.runToFuture(scheduler)
    }

    override def addMetadata(entries: Map[String, String]): Instance[In, Out] = {
      copy(metadata = metadata ++ entries)
    }
  }

  /**
    */
  case class VarSink[A](current: Var[A], override val metadata: Map[String, String], override val inputType: ContentType) extends DataSink {
    override type T      = VarSink[A]
    override type Input  = A
    override type Output = Unit

    override def addMetadata(entries: Map[String, String]): VarSink[A] = copy(metadata = metadata ++ entries)

    override def connect(contentType: ContentType, observable: Observable[A], sourceMetadata : Map[String, String])(implicit scheduler: Scheduler): CancelableFuture[Unit] = {
      observable.foreach { next =>
        current := next
      }
    }
  }

  def variable[A: TypeTag](currentFunction: Var[A], metadata: Map[String, String] = Map.empty): VarSink[A] = {
    val inputType: ContentType = ContentType.of[A]
    VarSink(currentFunction, metadata, inputType)
  }

  def count(metadata: Map[String, String] = Map.empty): Instance[Any, Long] = {
    val counter: Consumer.Sync[Any, Long] = Consumer.foldLeft(0L) {
      case (c, _) => c + 1
    }
    apply[Any, Long](counter, metadata)
  }

  def foreach[In: TypeTag](metadata: (String, String), theRest: (String, String)*)(thunk: In => Unit): Instance[In, Unit] = {
    foreach[In](theRest.toMap + metadata)(thunk)
  }
  def foreach[In: TypeTag](metadata: Map[String, String] = Map.empty)(thunk: In => Unit): Instance[In, Unit] = {
    apply[In, Unit](Consumer.foreach(thunk), metadata)
  }

  def apply[In: TypeTag, Out](consumer: Consumer[In, Out], metadata: Map[String, String] = Map.empty): Instance[In, Out] = {
    apply(consumer, metadata, ContentType.of[In])
  }

  def apply[In, Out](consumer: Consumer[In, Out], metadata: Map[String, String], contentType: ContentType): Instance[In, Out] = {
    new Instance(consumer, metadata, contentType)
  }

}
