package pipelines.reactive

import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.Var
import pipelines.reactive.trigger.{TriggerEvent, TriggerPipe, TriggerState}
import pipelines.socket.AddressedTextMessage

object PipelineService {
  def apply()(implicit scheduler: Scheduler): PipelineService = {
    val (sources, sinks, trigger) = TriggerPipe.create(scheduler)
    new PipelineService(sources, sinks, trigger)
  }
}

class PipelineService(sources: Sources, sinks: Sinks, triggers: TriggerPipe)(implicit scheduler: Scheduler) {
  private def manualSourceCriteria(userName: String) = {
    MetadataCriteria("user" -> userName, "manual" -> "true")
  }
  def getOrCreatePushSourceForUser(userName: String) = {
    import implicits._
    val obs = Observable.fromIterable(Seq(1, 2, 3)).asDataSource("user" -> userName, "manual" -> "true")

    val push = DataSource.push[AddressedTextMessage](Map("user" -> userName, "manual" -> "true"))
  }

  private var latest = Var(Option.empty[(TriggerState, TriggerEvent)])(scheduler)

  triggers.output.foreach {
    case entry @ (state: TriggerState, event: TriggerEvent) =>
      latest := Some(entry)
  }(triggers.scheduler)

  private def withState[T](f: TriggerState => T): Option[T] = {
    latest().map { e =>
      f(e._1)
    }
  }

  def cancelSource(userName: String) = {
    withState { state =>
//      val removed = state.sources.filter { src =>
//        criteria.matches(src.metadata)
//      }

    }

  }

}
