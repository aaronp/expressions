package pipelines.reactive.trigger

import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.ConcurrentSubject
import monix.reactive.{Observable, Observer}
import pipelines.reactive.{DataSink, DataSource, Transform}

import scala.concurrent.Future

object TriggerPipe {
  def apply(initialState: TriggerState = new TriggerState(Map.empty, Nil, Nil, Nil))(implicit scheduler: Scheduler): TriggerPipe = {
    new TriggerPipe(initialState)
  }
}

class TriggerPipe(initialState: TriggerState = new TriggerState(Map.empty, Nil, Nil, Nil))(implicit val scheduler: Scheduler) extends StrictLogging {

  def addTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean): Future[Ack] = {
    input.onNext(OnNewTrigger(trigger, retainTriggerAfterMatch))
  }
  def subscribeToSinks(sinks: Observable[DataSink]): Cancelable = {
    sinks.map(OnNewSink.apply).subscribe(input)
  }
  def subscribeToSources(sources: Observable[DataSource]): Cancelable = {
    sources.map(OnNewSource.apply).subscribe(input)
  }
  def addTransform(name: String, transform: Transform, replace: Boolean = false): Future[Ack] = {
    input.onNext(OnNewTransform(name, transform, replace))
  }

  val subject: ConcurrentSubject[TriggerInput, TriggerInput] = ConcurrentSubject.publishToOne[TriggerInput]
  val input: Observer[TriggerInput]                          = subject
  val output: Observable[(TriggerState, TriggerEvent)] = {
    val scanned = subject.scan(initialState -> (null: TriggerEvent)) {
      case ((state, _), input) => state.update(input)
    }
    scanned.share
  }
}
