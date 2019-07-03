package pipelines.reactive

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.Var
import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, RepoState, TriggerEvent, RepoStatePipe}

import scala.collection.concurrent

/**
  * An in-memory, JVM-side place to support:
  *
  * 1) CRUD of sources, transforms and sinks
  * 2) triggering 'pipelines' from #1 when matches occur
  * 3) updating existing pipelines
  *
  * The operations from this service are intended to be driven by e.g. a REST api, though don't necessarily need to be serializable
  *
  * @param sources
  * @param sinks
  * @param triggers
  * @param scheduler
  */
class PipelineService(val sources: Sources, val sinks: Sinks, val triggers: RepoStatePipe)(implicit scheduler: Scheduler) extends StrictLogging {

  private def addPipeline(id: UUID, pipeline: Pipeline[_, _]): Unit = {
    logger.info(s"!>! Pipeline added $id : $pipeline")
    pipelinesById.put(id, pipeline)
  }

  private var latest = Var(Option.empty[(RepoState, TriggerInput, TriggerEvent)])(scheduler)

  def state(): Option[RepoState]        = latest().map(_._1)
  def lastEvent(): Option[TriggerEvent] = latest().map(_._3)

  triggers.output.foreach { entry: (RepoState, TriggerInput, TriggerEvent) =>
    latest := Some(entry)
  }(triggers.scheduler)

  private val pipelinesById = {
    import scala.collection.JavaConverters._
    new java.util.concurrent.ConcurrentHashMap[UUID, Pipeline[_, _]]().asScala
  }

  def cancel(id: UUID): Option[Pipeline[_, _]] = {
    pipelinesById.remove(id) match {
      case None =>
        logger.info(s"Couldn't cancel the pipeline: $id")
        None
      case Some(p) =>
        logger.info(s"Canceled pipeline: $id")
        p.cancel()
        Some(p)
    }
  }
  def pipelines(): concurrent.Map[UUID, Pipeline[_, _]] = pipelinesById

  lazy val matchEvents: Observable[(TriggerInput, PipelineMatch)] = triggers.output
    .flatMap {
      case (_, input, event) => Observable.fromIterable(event.matches.map(input -> _))
    }
    .share(scheduler)

  lazy val pipelineCreatedEvents: Observable[Pipeline[_, _]] = matchEvents
    .flatMap {
      case (input: TriggerInput, mtch: PipelineMatch) =>
        onPipelineMatch(input, mtch) match {
          case Left(err) =>
            input.callback.onFailedMatch(input, mtch, err)
            logger.info(s"Couldn't create a pipeline: $err")
            Observable.empty[Pipeline[_, _]]
          case Right(pipeline: Pipeline[_, _]) =>
            logger.info(s"Pipeline '${pipeline.matchId}' added : $pipeline")
            input.callback.onMatch(input, mtch, pipeline)
            Observable(pipeline)
        }
    }
    .share(scheduler)

  def onPipelineMatch(input: TriggerInput, pipelineMatch: PipelineMatch): Either[String, Pipeline[_, _]] = {
    import pipelineMatch._
    Pipeline(pipelineMatch.matchId, source, transforms, sink.aux) { obs: Observable[pipelineMatch.sink.Input] =>
      obs.guarantee(Task.eval {
        logger.info(s"Pipeline removed '${pipelineMatch.matchId}' : $pipelineMatch")
        pipelinesById.remove(pipelineMatch.matchId)
      })
    }(scheduler)
  }

  def sourceMetadata(): Seq[Map[String, String]] = sources.list().map(_.metadata)
  def sinkMetadata(): Seq[Map[String, String]]   = sinks.list().map(_.metadata)

  def transformsById(): Map[String, Transform]               = state().fold(Map.empty[String, Transform])(_.transformsByName)
  def getOrCreateSource(source: DataSource): Seq[DataSource] = getOrCreateSource(MetadataCriteria(source.metadata), source)
  def getOrCreateSource(criteria: MetadataCriteria, source: => DataSource, callback: TriggerCallback = TriggerCallback.Ignore): Seq[DataSource] = {
    sources.find(criteria) match {
      case Seq() =>
        val (newSource, _) = sources.add(source, callback)
        Seq(newSource)
      case found => found
    }
  }
  def getOrCreateSink(sink: DataSink): Seq[DataSink] = getOrCreateSink(MetadataCriteria(sink.metadata), sink)
  def getOrCreateSink(criteria: MetadataCriteria, Sink: => DataSink): Seq[DataSink] = {
    sinks.find(criteria) match {
      case Seq() =>
        val (newSink, _) = sinks.add(Sink)
        Seq(newSink)
      case found => found
    }
  }
}

object PipelineService extends StrictLogging {
  def apply(transforms: Map[String, Transform] = Transform.defaultTransforms())(implicit scheduler: Scheduler): PipelineService = {
    val (sources, sinks, trigger) = create(scheduler)
    val service                   = new PipelineService(sources, sinks, trigger)

    transforms.foreach {
      case (id, t) => trigger.addTransform(id, t)
    }
    service.pipelineCreatedEvents.foreach { pipeline =>
      service.addPipeline(pipeline.matchId, pipeline)
    }

    service
  }

  private[reactive] def create(implicit scheduler: Scheduler): (Sources, Sinks, RepoStatePipe) = {
    val sources: Sources = Repo.sources(scheduler)
    val sinks: Sinks     = Repo.sinks(scheduler)
    val pipe             = RepoStatePipe()
    pipe.subscribeToSources(sources.events)
    pipe.subscribeToSinks(sinks.events)
    (sources, sinks, pipe)
  }
}
