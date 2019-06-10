package pipelines.reactive.trigger

import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.ConcurrentSubject
import monix.reactive.{Observable, Observer}
import pipelines.reactive._

import scala.concurrent.Future

/** The trigger pipe is the driver to push sources, sinks, transforms and triggers through a [[RepoState]]
  *
  * The 'output' is the hot observable stream of the trigger state and events
  *
  * @param initialState
  * @param scheduler
  */
class TriggerPipe(initialState: RepoState = new RepoState(Map.empty, Nil, Nil, Nil))(implicit val scheduler: Scheduler) extends StrictLogging {

  def connect(sourceCriteria: MetadataCriteria = MetadataCriteria(),
              sinkCriteria: MetadataCriteria = MetadataCriteria(),
              transforms: Seq[String] = Nil,
              callback: TriggerCallback = Ignore): Future[Ack] = {
    connect(sourceCriteria, sinkCriteria, transforms, false, callback)
  }

  def connect(sourceCriteria: MetadataCriteria,
              sinkCriteria: MetadataCriteria,
              transforms: Seq[String],
              retainTriggerAfterMatch: Boolean,
              callback: TriggerCallback): Future[Ack] = {
    connect(Trigger(sourceCriteria, sinkCriteria, transforms), retainTriggerAfterMatch, callback)
  }

  def connect(trigger: Trigger, retainTriggerAfterMatch: Boolean, callback: TriggerCallback): Future[Ack] = {
    input.onNext(OnNewTrigger(trigger, retainTriggerAfterMatch, callback))
  }

  def subscribeToSinks(sinks: Observable[TriggerInput with SinkEvent]): Cancelable       = sinks.subscribe(input)
  def subscribeToSources(sources: Observable[TriggerInput with SourceEvent]): Cancelable = sources.subscribe(input)
  def addTransform(name: String, transform: Transform, replace: Boolean = false, callback: TriggerCallback = Ignore): Future[Ack] = {
    input.onNext(OnNewTransform(name, transform, replace, callback))
  }

  private val subject: ConcurrentSubject[TriggerInput, TriggerInput] = ConcurrentSubject.publishToOne[TriggerInput]
  val input: Observer[TriggerInput]                                  = subject
  val output: Observable[(RepoState, TriggerEvent)] = {
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
  def apply(initialState: RepoState = new RepoState(Map.empty, Nil, Nil, Nil))(implicit scheduler: Scheduler): TriggerPipe = {
    new TriggerPipe(initialState)
  }
}
