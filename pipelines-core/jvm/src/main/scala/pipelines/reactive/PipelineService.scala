package pipelines.reactive

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.Var
import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent, TriggerPipe, TriggerState}

object PipelineService {
  def apply(transforms: Map[String, Transform] = Transform.defaultTransforms())(implicit scheduler: Scheduler): PipelineService = {
    val (sources, sinks, trigger) = TriggerPipe.create(scheduler)
    transforms.foreach {
      case (id, t) => trigger.addTransform(id, t)
    }
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
class PipelineService(val sources: Sources, val sinks: Sinks, val triggers: TriggerPipe)(implicit scheduler: Scheduler) extends StrictLogging {

  private var latest = Var(Option.empty[(TriggerState, TriggerEvent)])(scheduler)
  triggers.output.foreach { entry =>
    latest := Some(entry)
  }(triggers.scheduler)

  private val pipelinesById = {
    import scala.collection.JavaConverters._
    new java.util.concurrent.ConcurrentHashMap[UUID, Pipeline[_]]().asScala
  }

  def pipelineIds(): collection.Set[UUID] = pipelinesById.keySet
  def cancel(id: UUID): Option[Pipeline[_]] = {
    pipelinesById.remove(id).map { p =>
      p.cancel()
      p
    }
  }
  def pipelines(): Iterator[(UUID, Pipeline[_])] = pipelinesById.iterator


  val matchEvents: Observable[PipelineMatch] = triggers.output.flatMap {
    case (_, event) => Observable.fromIterable(event.matches)
  }
  val pipelineCreatedEvents: Observable[Pipeline[_]] = matchEvents.flatMap { pipelineMatch =>
    import pipelineMatch._
    val id = UUID.randomUUID()
    val either = Pipeline(source, transforms, sink.aux) { obs: Observable[pipelineMatch.sink.Input] =>
      obs.guarantee(Task.eval {
        logger.info(s"Pipeline removed '$id' : $pipelineMatch")
        pipelinesById.remove(id)
      })
    }(scheduler)

    either match {
      case Left(err) =>
        logger.info(s"Couldn't create a pipeline from $pipelineMatch: $err")
        Observable.empty
      case Right(pipeline) =>
        logger.info(s"Pipeline '$id' added : $pipelineMatch")
        pipelinesById.put(id, pipeline)
        Observable(pipeline)
    }
  }

  def state(): Option[TriggerState]     = latest().map(_._1)
  def lastEvent(): Option[TriggerEvent] = latest().map(_._2)

  def sourceMetadata(): Seq[Map[String, String]] = sources.list().map(_.metadata)
  def sinkMetadata(): Seq[Map[String, String]]   = sinks.list().map(_.metadata)

  def transformsById(): Map[String, Transform] = state().fold(Map.empty[String, Transform])(_.transformsByName)
  def getOrCreateSource(source: DataSource): Seq[DataSource] = getOrCreateSource(MetadataCriteria(source.metadata), source)
  def getOrCreateSource(criteria: MetadataCriteria, source: => DataSource): Seq[DataSource] = {
    sources.find(criteria) match {
      case Seq() =>
        val (newSource, _) = sources.add(source)
        Seq(newSource)
      case found => found
    }
  }
  def getOrCreateSink(Sink: DataSink): Seq[DataSink] = getOrCreateSink(MetadataCriteria(Sink.metadata), Sink)
  def getOrCreateSink(criteria: MetadataCriteria, Sink: => DataSink): Seq[DataSink] = {
    Sinks.find(criteria) match {
      case Seq() =>
        val (newSink, _) = Sinks.add(Sink)
        Seq(newSink)
      case found => found
    }
  }
}
