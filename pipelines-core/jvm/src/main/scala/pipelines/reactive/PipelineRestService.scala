package pipelines.reactive

import java.nio.file.Path
import java.util.UUID

import eie.io._
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import pipelines.Pipeline
import pipelines.reactive.DataSource.PushSource
import pipelines.reactive.PipelineRestService.Settings
import pipelines.rest.socket.AddressedTextMessage

import scala.collection.concurrent
import scala.concurrent.Future

object PipelineRestService {
  case class Settings(persistUnder: Path, ioScheduler: Scheduler)

  def apply(ioScheduler: Scheduler, dataDir: Path = "./data".asPath): PipelineRestService = {
    apply(Settings(dataDir, ioScheduler))
  }

  def apply(settings: Settings): PipelineRestService = {
    import settings._
    val underlying = PipelineService()(ioScheduler)

    underlying.triggers.addTransform("persisted", JVMTransforms.writeToZipped(persistUnder))

    underlying.sinks.add(DataSink.count(Map(tags.Name       -> "count")))
    underlying.sinks.add(RegisterAsSourceSink(Map(tags.Name -> "register", "prefix" -> "register"), underlying.sources))

    new PipelineRestService(settings, underlying, ioScheduler)
  }
}

/**
  * Puts some additional sugar on top of the PipelineService to adapt it for use by our REST endpoints ...
  *
  * whatever additional plumbing noise, or adapting types to be the REST DTOs should go here instead of bulking up the
  * [[PipelineService]]
  *
  * @param settings
  * @param underlying
  * @param ioScheduler
  */
class PipelineRestService(val settings: Settings, val underlying: PipelineService, ioScheduler: Scheduler) {

  def sinks(): Seq[DataSink] = {
    underlying.state().fold(Seq[DataSink]()) { st8 =>
      st8.sinks
    }
  }
  def transformsByName(): Map[String, Transform] = {
    underlying.state().fold(Map[String, Transform]()) { st8 =>
      st8.transformsByName
    }
  }
  def sources(): Seq[DataSource] = {
    underlying.state().fold(Seq[DataSource]()) { st8 =>
      st8.sources
    }
  }
  def pipelines(): concurrent.Map[UUID, Pipeline[_, _]] = underlying.pipelines

  def pushSourceFor(id: String): Option[PushSource[AddressedTextMessage]] = {
    sourceFor(id).collect {
      case push: PushSource[AddressedTextMessage] => push
    }
  }

  def sourceFor(id: String): Option[DataSource] = {
    underlying.state().flatMap { st8 =>
      st8.sourcesById.get(id) match {
        case Some(src) => Option(src)
        case _         => None
      }
    }
  }

  def connect(sourceCriteria: MetadataCriteria = MetadataCriteria(),
              sinkCriteria: MetadataCriteria = MetadataCriteria(),
              transforms: Seq[String] = Nil,
              callback: TriggerCallback = TriggerCallback.Ignore): Future[Ack] = {
    underlying.triggers.connect(sourceCriteria, sinkCriteria, transforms, false, callback)
  }

  /** Registers a transformation for 'name' which will combine the matching source with the 'joinLatest' against an input source.
    *
    * @param name the name of the new transformation
    * @param criteria the criteria used to find what should be a single unique data source with which to join
    * @param replace a flag to determine what to do if an existing transform is already registered with the given name
    * @param callback a callback to invoke with the source is registered
    * @return either the new transform or a left error message
    */
  def getOrCreateJoinLatestTransform(name: String,
                                     criteria: MetadataCriteria,
                                     replace: Boolean = false,
                                     callback: TriggerCallback = TriggerCallback.Ignore): Either[String, (Transform, Future[Ack])] = {
    underlying.getOrCreateJoinTransform(name, criteria, replace, callback)(Transform.combineLatest)
  }

  def getOrCreateDump(text: String) = underlying.triggers.addTransform(text, Transform.dump(text))

  def getOrCreatePushSource(metadata: Map[String, String], callback: TriggerCallback = TriggerCallback.Ignore): (DataSource, Future[Ack]) = {
    implicit val wtf = ioScheduler
    val push         = DataSource.push[AddressedTextMessage](metadata)
    underlying.sources.add(push, callback)
  }

  /**
    * This should be able to support users creating their own configs (e.g. w/ 'foo.hostname=custom') which can be parsed
    * into a typesafe config (via a transformation) or just normal e.g. Json values which could be pushed into something else.
    *
    * @param metadata
    */
  def getOrCreateStringSource(content: String, metadata: Map[String, String], callback: TriggerCallback = TriggerCallback.Ignore): (DataSource, Future[Ack]) = {
    val source = DataSource[String](Observable(content)).addMetadata(metadata)
    underlying.sources.add(source, callback)
  }

}
