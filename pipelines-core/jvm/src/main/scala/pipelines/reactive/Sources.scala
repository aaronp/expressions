package pipelines.reactive

import java.util.UUID

import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}

import scala.concurrent.Future

/**
  * A collection of available [[DataSource]]s
  *
  * @param input the feed into our observable
  * @param nextSources the observable which notifies listeners of new sources
  * @param scheduler
  */
class Sources(private val input: Observer[DataSource], val nextSources: Observable[DataSource])(implicit scheduler: Scheduler) {

  private object Lock
  private var sourcesById = Map[String, DataSource]()

  def add(source: DataSource): (DataSource, Future[Ack]) = {
    val id: String           = UUID.randomUUID().toString
    val idSource: DataSource = source.addMetadata(UniqueSourceId, id)
    Lock.synchronized {
      sourcesById = sourcesById.updated(id, idSource)
    }
    idSource -> input.onNext(idSource)
  }

  /** @return an infinite observable of all existing and future sources
    */
  def sources: Observable[DataSource] = {
    val next = nextSources.publish(scheduler)
    Observable.fromIterable(sourcesById.values) ++ next
  }

}

object Sources {

  def apply(implicit scheduler: Scheduler): Sources = {
    val (input: Observer[DataSource], output: Observable[DataSource]) = Pipe.publish[DataSource].multicast
    new Sources(input, output)
  }

}
