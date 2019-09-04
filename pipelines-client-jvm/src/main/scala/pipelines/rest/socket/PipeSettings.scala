package pipelines.rest.socket

import monix.execution.Scheduler
import monix.reactive.subjects.ConcurrentSubject
import monix.reactive.{Observable, Observer, Pipe}
import pipelines.reactive.NoCompleteObserver

case class PipeSettings(replay: Int = 0, concurrentSubject: Boolean = false)
object PipeSettings {
  def pipeForSettings(name: String, settings: PipeSettings)(implicit scheduler: Scheduler): (Observer[AddressedMessage], Observable[AddressedMessage]) = {
    val (in, out: Observable[AddressedMessage]) = settings match {
      case PipeSettings(replay, true) if replay < 1 =>
        val sub = ConcurrentSubject.publish[AddressedMessage](scheduler)
        (sub, sub.share(scheduler).dump(s"$name [CP]"))
      case PipeSettings(replay, true) =>
        val sub = ConcurrentSubject.replayLimited[AddressedMessage](replay)(scheduler)
        (sub, sub.share(scheduler).dump(s"$name [C-${replay}]"))
      case PipeSettings(replay, false) if replay < 1 =>
        val (in, out) = Pipe.publishToOne[AddressedMessage].unicast //(scheduler)
        (in, out.share(scheduler).dump(s"$name [P]"))
      case PipeSettings(replay, false) =>
        val (in, out) = Pipe.replayLimited[AddressedMessage](replay).multicast(scheduler)
        (in, out.share(scheduler).dump(s"$name [P-${replay}]"))

    }

    if (settings.concurrentSubject) {
      (NoCompleteObserver(in), out)
    } else {
      (in, out)
    }
  }
}
