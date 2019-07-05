package pipelines.reactive.trigger

import com.typesafe.scalalogging.StrictLogging
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.subjects.{ConcurrentSubject, Var}
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
class RepoStatePipe(initialState: RepoState = new RepoState(Map.empty, Nil, Nil, Nil))(implicit val scheduler: Scheduler) extends StrictLogging {

  def connect(sourceCriteria: MetadataCriteria,
              sinkCriteria: MetadataCriteria,
              transforms: Seq[String] = Nil,
              retainTriggerAfterMatch: Boolean = false,
              callback: TriggerCallback = TriggerCallback.Ignore): Future[Ack] = {
    connect(Trigger(sourceCriteria, sinkCriteria, transforms), retainTriggerAfterMatch, callback)
  }

  /**
    * Triggers a match between sources and sinks
    *
    * @param trigger
    * @param retainTriggerAfterMatch
    * @param callback
    * @return
    */
  def connect(trigger: Trigger, retainTriggerAfterMatch: Boolean, callback: TriggerCallback): Future[Ack] = {
    input.onNext(OnNewTrigger(trigger, retainTriggerAfterMatch, callback))
  }

  def subscribeToSinks(sinks: Observable[TriggerInput with SinkEvent]): Cancelable       = sinks.subscribe(input)
  def subscribeToSources(sources: Observable[TriggerInput with SourceEvent]): Cancelable = sources.subscribe(input)
  def addTransform(name: String, transform: Transform, replace: Boolean = false, callback: TriggerCallback = TriggerCallback.Ignore): Future[Ack] = {
    input.onNext(OnNewTransform(name, transform, replace, callback))
  }

  private val subject: ConcurrentSubject[TriggerInput, TriggerInput] = ConcurrentSubject.publishToOne[TriggerInput]
  private val currentStateVar                                        = Var[RepoState](null)
  def currentState(): Option[RepoState]                              = Option(currentStateVar.apply())
  val input: Observer[TriggerInput]                                  = subject
  val output: Observable[(RepoState, TriggerInput, TriggerEvent)] = {
    val scanned = subject.scan((initialState, (null: TriggerInput), (null: TriggerEvent))) {
      case ((state, _, _), input) =>
        val (newState, result) = state.update(input)
        logger.debug(s"$input yields $result w/ state having ${newState.sources.size} sources, ${newState.sinks.size} sinks, and ${newState.triggers.size} triggers")
        currentStateVar := newState
        (newState, input, result)
    }
    scanned.share
  }
}

object RepoStatePipe {

  def apply(initialState: RepoState = new RepoState(Map.empty, Nil, Nil, Nil))(implicit scheduler: Scheduler): RepoStatePipe = {
    new RepoStatePipe(initialState)
  }
}
