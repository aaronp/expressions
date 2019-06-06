package pipelines.reactive

import monix.execution.Scheduler
import monix.reactive.subjects.Var
import pipelines.reactive.trigger.{TriggerEvent, TriggerPipe, TriggerState}

object PipelineService {
  def apply()(implicit scheduler: Scheduler): PipelineService = {
    val (sources, sinks, trigger) = TriggerPipe.create(scheduler)
    new PipelineService(sources, sinks, trigger)
  }
}

/**
  * A place to support:
  *
  * 1) CRUD of sources, transforms and sinks
  * 2) triggering 'pipelines' from #1 when matches occur
  * 3) updating existing pipelines
  *
  * @param sources
  * @param sinks
  * @param triggers
  * @param scheduler
  */
class PipelineService(val sources: Sources, val sinks: Sinks, val triggers: TriggerPipe)(implicit scheduler: Scheduler) {

  private var latest = Var(Option.empty[(TriggerState, TriggerEvent)])(scheduler)
  triggers.output.foreach { entry =>
    latest := Some(entry)
  }(triggers.scheduler)

  def state(): Option[TriggerState]     = latest().map(_._1)
  def lastEvent(): Option[TriggerEvent] = latest().map(_._2)

  def sourceMetadata(): Seq[Map[String, String]] = sources.list().map(_.metadata)
  def sinkMetadata(): Seq[Map[String, String]]   = sinks.list().map(_.metadata)
  def transforms()                               = state().fold(Map.empty[String, Transform])(_.transformsByName)

  def getOrCreateSource(source: DataSource): Seq[DataSource] = {
    val criteria: MetadataCriteria = MetadataCriteria(source.metadata)
    getOrCreateSource(criteria, source)
  }
  def getOrCreateSource(criteria: MetadataCriteria, source: => DataSource): Seq[DataSource] = {
    sources.find(criteria) match {
      case Seq() =>
        val (newSource, _) = sources.add(source)
        Seq(newSource)
      case found => found
    }
  }
}
