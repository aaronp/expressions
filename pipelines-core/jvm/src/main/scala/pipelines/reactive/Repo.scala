package pipelines.reactive

import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}

import scala.concurrent.Future

/**
  * An observable DAO representation, used to manage our sources and sinks
  *
  * @param input
  * @param nextObs
  * @param addId
  * @param addEvent
  * @param removeEvent
  * @param scheduler
  * @tparam Event
  * @tparam A
  */
class Repo[Event, A <: HasMetadata](private val input: Observer[Event],
                                    nextObs: Observable[Event],
                                    addId: (A, String) => A,
                                    addEvent: (A, TriggerCallback) => Event,
                                    removeEvent: (A, TriggerCallback) => Event)(implicit val scheduler: Scheduler) {

  private object Lock
  private var byId   = Map[String, A]()
  private var byName = Map[String, A]()

  def find(criteria: MetadataCriteria): Seq[A] = {
    byId.values.filter(x => criteria.matches(x.metadata)).toSeq
  }

  def list(): Seq[A] = byId.values.toSeq

  def size() = byId.size

  def forId(id: String): Option[A]     = byId.get(id)
  def forName(name: String): Option[A] = byName.get(name)

  def remove(id: String, callback: TriggerCallback = TriggerCallback.Ignore): Option[Future[Ack]] = {
    val removed = Lock.synchronized {
      val before: Option[A] = byId.get(id)
      before.foreach { value =>
        value.name.foreach { name =>
          byName = byName - name
        }
      }
      byId = byId - id
      before
    }
    removed.map { instance =>
      input.onNext(removeEvent(instance, callback))
    }
  }
  def add[T <: A](value: T, callback: TriggerCallback = TriggerCallback.Ignore, id: String = Ids.next()): (T, Future[Ack]) = {
    val idSource: T = addId(value, id).asInstanceOf[T]
    Lock.synchronized {
      byId = byId.updated(id, idSource)
      value.name.foreach { key =>
        byName = byName.updated(key, idSource)
      }
    }
    idSource -> input.onNext(addEvent(idSource, callback))
  }

  /** @return an infinite observable of all existing and future values
    */
  def events: Observable[Event] = {
    val existingEvents = Observable.fromIterable(byId.values).map(addEvent(_, TriggerCallback.Ignore))
    existingEvents ++ nextObs
  }
}

object Repo {
  def sources(implicit scheduler: Scheduler): Repo[SourceEvent, DataSource] = {
    val (input: Observer[SourceEvent], output: Observable[SourceEvent]) = Pipe.publish[SourceEvent].multicast
    new Repo[SourceEvent, DataSource](input, output, (src: DataSource, id: String) => src.ensuringId(id), OnSourceAdded.apply, OnSourceRemoved.apply)
  }
  def sinks(implicit scheduler: Scheduler): Repo[SinkEvent, DataSink] = {
    val (input: Observer[SinkEvent], output: Observable[SinkEvent]) = Pipe.publish[SinkEvent].multicast
    new Repo[SinkEvent, DataSink](input, output, (sink: DataSink, id: String) => sink.ensuringId(id), OnSinkAdded.apply, OnSinkRemoved.apply)
  }

}
