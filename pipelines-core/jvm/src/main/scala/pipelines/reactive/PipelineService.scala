package pipelines.reactive

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.subjects.Var
import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, RepoState, TriggerEvent, TriggerPipe}

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
class PipelineService(val sources: Sources, val sinks: Sinks, val triggers: TriggerPipe)(implicit scheduler: Scheduler) extends StrictLogging {

  private def addPipeline(id: UUID, pipeline: Pipeline[_]): Unit = {
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
    new java.util.concurrent.ConcurrentHashMap[UUID, Pipeline[_]]().asScala
  }

  def cancel(id: UUID): Option[Pipeline[_]] = {
    pipelinesById.remove(id).map { p =>
      p.cancel()
      p
    }
  }
  def pipelines(): concurrent.Map[UUID, Pipeline[_]] = pipelinesById

  lazy val matchEvents: Observable[(TriggerInput, PipelineMatch)] = triggers.output
    .flatMap {
      case (_, input, event) => Observable.fromIterable(event.matches.map(input -> _))
    }
    .share(scheduler)

  lazy val pipelineCreatedEvents: Observable[Pipeline[_]] = matchEvents
    .dump("match event")
    .flatMap {
      case (input: TriggerInput, mtch: PipelineMatch) =>
        onPipelineMatch(input, mtch) match {
          case Left(err) =>
            input.callback.onFailedMatch(input, mtch, err)
            logger.info(s"Couldn't create a pipeline: $err")
            Observable.empty[Pipeline[_]]
          case Right(pipeline: Pipeline[_]) =>
            logger.info(s"Pipeline '${pipeline.matchId}' added : $pipeline")
            input.callback.onMatch(input, mtch, pipeline)
            Observable(pipeline)
        }
    }
    .share(scheduler)

  def onPipelineMatch(input: TriggerInput, pipelineMatch: PipelineMatch): Either[String, Pipeline[pipelineMatch.sink.Output]] = {
    import pipelineMatch._
    Pipeline(pipelineMatch.matchId, source, transforms, sink.aux) { obs: Observable[pipelineMatch.sink.Input] =>
      obs.guarantee(Task.eval {
        logger.info(s"Pipeline removed '${pipelineMatch.matchId} : $pipelineMatch")
        pipelinesById.remove(pipelineMatch.matchId)
      })
    }(scheduler)
  }

  def sourceMetadata(): Seq[Map[String, String]] = sources.list().map(_.metadata)
  def sinkMetadata(): Seq[Map[String, String]]   = sinks.list().map(_.metadata)

  def transformsById(): Map[String, Transform]               = state().fold(Map.empty[String, Transform])(_.transformsByName)
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

object PipelineService extends StrictLogging {
  def apply(transforms: Map[String, Transform] = Transform.defaultTransforms())(implicit scheduler: Scheduler): PipelineService = {
    val (sources, sinks, trigger) = TriggerPipe.create(scheduler)
    transforms.foreach {
      case (id, t) => trigger.addTransform(id, t)
    }
    val service = new PipelineService(sources, sinks, trigger)

    service.pipelineCreatedEvents.dump("pipeline created").foreach { pipeline =>
      service.addPipeline(pipeline.matchId, pipeline)
    }

    service
  }
}
