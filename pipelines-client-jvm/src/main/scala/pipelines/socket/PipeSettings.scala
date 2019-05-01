package pipelines.socket

import monix.execution.Scheduler
import monix.reactive.{Observable, Observer, Pipe}
import monix.reactive.subjects.ConcurrentSubject

case class PipeSettings(replay: Int = 0, concurrentSubject: Boolean = false)
object PipeSettings {
  def pipeForSettings(name: String, settings: PipeSettings)(implicit scheduler: Scheduler): (Observer[AddressedMessage], Observable[AddressedMessage]) = {
    settings match {
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
  }
}
