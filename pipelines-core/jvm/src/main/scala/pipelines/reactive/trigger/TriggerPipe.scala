package pipelines.reactive.trigger

import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.ConcurrentSubject
import monix.reactive.{Observable, Observer}
import pipelines.reactive._

import scala.concurrent.Future

/** The trigger pipe is the driver to push sources, sinks, transforms and triggers through a [[TriggerState]]
  *
  * The 'output' is the hot observable stream of the trigger state and events
  *
  * @param initialState
  * @param scheduler
  */
class TriggerPipe(initialState: TriggerState = new TriggerState(Map.empty, Nil, Nil, Nil))(implicit val scheduler: Scheduler) extends StrictLogging {

  def triggerMatch(sourceCriteria: MetadataCriteria = MetadataCriteria(), sinkCriteria: MetadataCriteria = MetadataCriteria(), transforms: Seq[String] = Nil): Future[Ack] = {
    addTrigger(sourceCriteria, sinkCriteria, transforms, false)
  }

  def addTrigger(sourceCriteria: MetadataCriteria = MetadataCriteria(),
                 sinkCriteria: MetadataCriteria = MetadataCriteria(),
                 transforms: Seq[String] = Nil,
                 retainTriggerAfterMatch: Boolean = true): Future[Ack] = {
    addTrigger(Trigger(sourceCriteria, sinkCriteria, transforms), retainTriggerAfterMatch)
  }

  def addTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean): Future[Ack] = {
    input.onNext(OnNewTrigger(trigger, retainTriggerAfterMatch))
  }
  def subscribeToSinks(sinks: Observable[TriggerInput with SinkEvent]): Cancelable = sinks.subscribe(input)
  def subscribeToSources(sources: Observable[TriggerInput with SourceEvent]): Cancelable = sources.subscribe(input)
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

object TriggerPipe {

  def create(implicit scheduler: Scheduler) = {
    val sources: Sources = Repo.sources(scheduler)
    val sinks: Sinks     = Repo.sinks(scheduler)
    val instance         = apply()

    instance.subscribeToSources(sources.events.dump("sources"))
    instance.subscribeToSinks(sinks.events.dump("sinks"))
    (sources, sinks, instance)
  }
  def apply(initialState: TriggerState = new TriggerState(Map.empty, Nil, Nil, Nil))(implicit scheduler: Scheduler): TriggerPipe = {
    new TriggerPipe(initialState)
  }
}
