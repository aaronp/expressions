package pipelines.reactive

import monix.execution.Ack
import monix.reactive.Observer

import scala.concurrent.Future

final case class NoCompleteObserver[A](underlying: Observer[A]) extends Observer[A] {
  override def onNext(elem: A): Future[Ack] = underlying.onNext(elem)

  override def onError(ex: Throwable): Unit = underlying.onError(ex)

  override def onComplete(): Unit = {}
}
