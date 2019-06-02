package pipelines.reactive

import java.util.UUID

import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}

import scala.concurrent.Future

case class Sinks(private val input: Observer[DataSink], val nextSinks: Observable[DataSink])(implicit scheduler: Scheduler) {

  private object Lock

  private var sinksById = Map[String, DataSink]()

  def add(sink: DataSink): Future[Ack] = {
    val id: String = UUID.randomUUID().toString
    val idSink     = sink.addMetadata(UniqueSinkId, id)
    Lock.synchronized {
      sinksById = sinksById.updated(id, idSink)
    }
    input.onNext(idSink)
  }

  /** @return an infinite observable of all existing and future sources
    */
  def sinks: Observable[DataSink] = {
    (Observable.fromIterable(sinksById.values) ++ nextSinks)//.share
  }
}

object Sinks {

  def apply(implicit scheduler: Scheduler): Sinks = {
    val (input: Observer[DataSink], output: Observable[DataSink]) = Pipe.publish[DataSink].multicast
    new Sinks(input, output)
  }

}
