package pipelines.reactive

import java.util.UUID

import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.subjects.Var
import pipelines.Pipeline
import pipelines.reactive.repo.{ListRepoSourcesResponse, ListSinkResponse, ListedDataSink, ListedDataSource}
import pipelines.reactive.trigger.{PipelineMatch, RepoState, RepoStatePipe, TriggerEvent}
import pipelines.rest.socket.AddressedMessage

import scala.collection.concurrent
import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

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
class PipelineService(val sources: Sources, val sinks: Sinks, val streamDao: StreamDao[Future], val triggers: RepoStatePipe)(implicit scheduler: Scheduler) extends StrictLogging {

  private val latest = Var(Option.empty[(RepoState, TriggerInput, TriggerEvent)])(scheduler)

  private val pipelinesById: concurrent.Map[UUID, Pipeline[_, _]] = {
    import scala.collection.JavaConverters._
    new java.util.concurrent.ConcurrentHashMap[UUID, Pipeline[_, _]]().asScala
  }

  /**
    * an observable of 'matches' containing the input (a new source, sink, trigger) with the 'match' created from it.
    *
    */
  lazy val matchEvents: Observable[(TriggerInput, PipelineMatch)] = triggers.output
    .flatMap {
      case (newState: RepoState, input: TriggerInput, event: TriggerEvent) =>
        onEvent(newState, input, event)
        Observable.fromIterable(event.matches.map(input -> _))
    }
    .share(scheduler)

  private def onEvent(newState: RepoState, input: TriggerInput, event: TriggerEvent): Unit = {

    logger.info(s"""
        !VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV _-=[ onEvent ]=-_ VVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV
        !
        !input: $input
        !
        !result $event
        !
        !${RepoStateRender(this, newState)}
        !^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
      """.stripMargin('!'))
  }

  /**
    * An event stream of Pipelines -- joined up sources and sinks
    */
  lazy val pipelineCreatedEvents: Observable[Pipeline[_, _]] = {
    matchEvents
      .flatMap {
        case (input: TriggerInput, mtch: PipelineMatch) =>
          onPipelineMatch(input, mtch) match {
            case Left(err) =>
              input.callback.onFailedMatch(input, mtch, err)
              logger.info(s"Couldn't create a pipeline: $err")
              Observable.empty[Pipeline[_, _]]
            case Right(pipeline: Pipeline[_, _]) =>
              logger.info(s"!!!Pipeline '${pipeline.matchId}' added due to\n\t$input\n\tWith Trigger:\n\t${mtch.trigger}\n\tPipeline:\n\t$pipeline")
              input.callback.onMatch(input, mtch, pipeline)
              Observable(pipeline)
          }
      }
      .map { pipeline =>
        // let's be sure this is only evaluated once
        addPipeline(pipeline.matchId, pipeline)
        pipeline
      }
      .share(scheduler)
  }

  override def toString: String = {
    pipelines
      .map {
        case (id, p) =>
          s"""$id
         |\t$p
       """.stripMargin
      }
      .mkString("\n")
  }

  private def addPipeline(id: UUID, pipeline: Pipeline[_, _]): Unit = {
    logger.info(s"!>! Pipeline added $id : $pipeline")
    pipelinesById.put(id, pipeline)
  }

  def listSources(queryParams: Map[String, String]): ListRepoSourcesResponse = {
    val criteria = MetadataCriteria(queryParams)

    val results = sources.list().withFilter(ds => criteria.matches(ds.metadataWithContentType)).map { found: DataSource =>
      new ListedDataSource(found.metadataWithContentType, Option(found.contentType))
    }
    ListRepoSourcesResponse(results)
  }

  def listSinks(queryParams: Map[String, String]): ListSinkResponse = {
    val criteria = MetadataCriteria(queryParams)
    val results = sinks.list().withFilter(ds => criteria.matches(ds.metadata)).map { sink =>
      new ListedDataSink(sink.metadata, sink.inputType)
    }
    ListSinkResponse(results)
  }

  def state(): Option[RepoState]        = latest().map(_._1)
  def lastEvent(): Option[TriggerEvent] = latest().map(_._3)

  triggers.output.foreach { entry: (RepoState, TriggerInput, TriggerEvent) =>
    latest := Some(entry)
  }(triggers.scheduler)

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
  def pipelinesForSource(sourceId: String) = {
    pipelines().values.filter(_.sourceId == sourceId)
  }
  def pipelinesForSink(sinkId: String) = {
    pipelines().values.filter(_.sinkId == sinkId)
  }
  def pipelinesForSourceAndSink(sourceId: String, sinkId: String) = {
    pipelines().values.filter { p =>
      p.root.id.exists(_ == sourceId) &&
      p.sink.id.exists(_ == sinkId)
    }
  }

  def onPipelineMatch(input: TriggerInput, pipelineMatch: PipelineMatch): Either[String, Pipeline[_, _]] = {
    import pipelineMatch._

    val matchSourceIds = pipelineMatch.source.id.toSeq
    val matchSinkIds   = pipelineMatch.sink.id.toSeq

    val foundPipelines: Seq[Pipeline[_, _]] = for {
      sourceId <- matchSourceIds
      sinkId   <- matchSinkIds
      p        <- pipelinesForSourceAndSink(sourceId, sinkId)
    } yield {
      p
    }

    def create() = {
      Pipeline(pipelineMatch.matchId, source, transforms, sink.aux) { obs: Observable[pipelineMatch.sink.Input] =>
        obs
          .guarantee(Task.eval {
            logger.info(s"Pipeline removed '${pipelineMatch.matchId}' : $pipelineMatch")
            pipelinesById.remove(pipelineMatch.matchId)
          })
      }(scheduler)
    }

    if (foundPipelines.nonEmpty) {
      foundPipelines.foreach(println)
      val result = Left(
        s"Found ${foundPipelines.size} pipelines for ${matchSourceIds.size} sourceIds ${matchSourceIds.mkString("[", ",", "]")} and ${matchSinkIds.size} sinkIds ${matchSinkIds
          .mkString("[", ",", "]")} ...\nSource ${pipelineMatch.source} is already connected to ${pipelineMatch.sink}: ${foundPipelines.map(_.matchId)}}")
      println()
      result
    } else {
      create()
    }
  }

  def sourceMetadata(): Seq[Map[String, String]] = sources.list().map(_.metadata)
  def sinkMetadata(): Seq[Map[String, String]]   = sinks.list().map(_.metadata)

  def transformsById(): Map[String, Transform] = state().fold(Map.empty[String, Transform])(_.transformsByName)

  /**
    * A means to get a source for a particular name (not ID), and potentially create a new one if one does not already exist.
    *
    * If one exists, the queryParams and persist flag are NOT used. If a new source IS created, then the 'persist' determines whether
    * the source is written to persistent storage (e.g. a new source w/ the same name should be loaded on startup)
    *
    * @param name
    * @param createIfMissing
    * @param persist
    * @param queryParams
    * @tparam A
    * @return a future detailing if the source was created and the push source, either new or created
    */
  def pushSourceForName[A: TypeTag](name: String, createIfMissing: Boolean, persist: Boolean, queryParams: Map[String, String]): Future[(Boolean, DataSource.PushSource[A])] = {
    getOrCreateSourceForName[DataSource.PushSource[A]](name, createIfMissing, persist) {
      val metadata = queryParams.updated(tags.Name, name)
      DataSource
        .push[A](metadata)
        .ensuringId(Ids.next())
        .ensuringMetadata(tags.SourceType, tags.typeValues.Push)
        .ensuringContentType()
    }
  }

  def getOrCreateSinkForName[S <: DataSink: ClassTag](name: String, createIfMissing: Boolean, persist: Boolean)(newSink: => S): Future[(Boolean, S)] = {
    sinks.forName(name) match {
      case Some(src: S) => Future.successful(false -> src)
      case Some(other)  => Future.failed(new Exception(s"Sink '$name' is not a ${implicitly[ClassTag[S]].runtimeClass.getName}: $other"))
      case None if createIfMissing =>
        val dataSink: S = newSink
        def readBack(): Future[(Boolean, S)] = {
          getOrCreateSink(MetadataCriteria(Map(tags.Name -> name)), dataSink) match {
            case Seq()         => Future.failed(new Exception(s"Compute says no  - sink $name not created"))
            case Seq(found: S) => Future.successful(true -> found)
            case many          => Future.failed(new Exception(s"Race condition creating sink as we found ${many.size} sinks for $name"))
          }
        }
        if (persist) {
          streamDao.persistSource(dataSink.metadata).flatMap { _ =>
            readBack()
          }
        } else {
          readBack()
        }
      case None =>
        Future.failed(new Exception(s"Sink '${name}' doesn't exist and 'createIfMissing' query parameter not set"))
    }
  }

  /**
    *
    * @param name the source name
    * @param createIfMissing
    * @param persist
    * @param newSource
    * @tparam S
    * @return a tuple of a boolean to indicate if a new source was created (false if it was found) for the given name
    */
  def getOrCreateSourceForName[S <: DataSource: ClassTag](name: String, createIfMissing: Boolean, persist: Boolean)(newSource: => S): Future[(Boolean, S)] = {
    sources.forName(name) match {
      case Some(src: S) => Future.successful(false -> src)
      case Some(other)  => Future.failed(new Exception(s"Source '$name' is not a ${implicitly[ClassTag[S]].runtimeClass.getName}: $other"))
      case None if createIfMissing =>
        val dataSource: S = newSource

        def readBack(): Future[(Boolean, S)] = {
          getOrCreateSource(MetadataCriteria(Map(tags.Name -> name)), dataSource) match {
            case Seq()         => Future.failed(new Exception(s"Compute says no  - source $name not created"))
            case Seq(found: S) => Future.successful(true -> found)
            case many          => Future.failed(new Exception(s"Race condition creating source as we found ${many.size} sources for $name"))
          }
        }
        if (persist) {
          streamDao.persistSource(dataSource.metadata).flatMap { _ =>
            readBack()
          }
        } else {
          readBack()
        }
      case None =>
        Future.failed(new Exception(s"Source '${name}' doesn't exist and 'createIfMissing' query parameter not set"))
    }
  }

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

  def addTransform(name: String, newTransform: Transform, replace: Boolean = false, callback: TriggerCallback = TriggerCallback.Ignore) = {
    triggers.addTransform(name, newTransform, replace, callback)
  }

  /** Registers a transformation for 'name' which will combine the matching source with the 'joinLatest' against an input source.
    *
    * @param name the name of the new transformation
    * @param criteria the criteria used to find what should be a single unique data source with which to join
    * @param replace a flag to determine what to do if an existing transform is already registered with the given name
    * @param callback a callback to invoke with the source is registered
    * @return either the new transform or a left error message
    */
  def getOrCreateJoinTransform(name: String, criteria: MetadataCriteria, replace: Boolean = false, callback: TriggerCallback = TriggerCallback.Ignore)(
      transformSource: DataSource => Transform): Either[String, (Transform, Future[Ack])] = {
    sources.find(criteria) match {
      case Seq(only: DataSource) =>
        val newTransform: Transform = transformSource(only)
        val ack                     = addTransform(name, newTransform, replace, callback)
        Right(newTransform -> ack)
      case Seq() =>
        sources.list() match {
          case Seq()     => Left(s"There are no registered sources to match")
          case Seq(only) => Left(s"The source '${only.metadata.mkString("[", ",", "]")}' doesn't match")
          case Seq(_, _) => Left(s"Neither of the 2 sources match")
          case many      => Left(s"None of the ${many.size} sources match")
        }
      case many => Left(s"${many.size} of the ${sources.size} sources match")
    }
  }

}

object PipelineService extends StrictLogging {

  def pipelineAsAddressedMessage(pipeline: Pipeline[_, _]): AddressedMessage = {
    AddressedMessage(pipeline.asDto)
  }

  def registerOwnSourcesAndSink(service: PipelineService): PipelineService = {
    service.getOrCreateSource {
      DataSource(service.sources.events).addMetadata(tags.Label, tags.labelValues.SourceEvents)
    }
    service.getOrCreateSource {
      DataSource(service.sinks.events).addMetadata(tags.Label, tags.labelValues.SinkEvents)
    }
    service.getOrCreateSource {
      DataSource(service.pipelineCreatedEvents).addMetadata(tags.Label, tags.labelValues.PipelineCreatedEvents)
    }
    service.getOrCreateSource {
      DataSource(service.matchEvents).addMetadata(tags.Label, tags.labelValues.MatchEvents)
    }

    service.addTransform(tags.transforms.`SourceEvent.asAddressedMessage`, Transform.map(SourceEvent.asAddressedMessage))
    service.addTransform(tags.transforms.`SinkEvent.asAddressedMessage`, Transform.map(SinkEvent.asAddressedMessage))
    service.addTransform(tags.transforms.`Pipeline.asAddressedMessage`, Transform.map(pipelineAsAddressedMessage))

    service
  }

  def apply(transforms: Map[String, Transform] = Transform.defaultTransforms(), dao: StreamDao[Future] = StreamDao.noop[Future])(implicit scheduler: Scheduler): PipelineService = {
    val (sources, sinks, trigger) = create(scheduler)
    val service                   = new PipelineService(sources, sinks, dao, trigger)

    transforms.foreach {
      case (id, t) => trigger.addTransform(id, t)
    }
    // ensure our lazily-created stream is observed here, as the observation of which will drive an underlying pipelinemap
    service.pipelineCreatedEvents.foreach { pipeline =>
      logger.info(s"created $pipeline")
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
