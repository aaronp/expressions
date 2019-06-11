package pipelines.reactive

import java.nio.file.Path
import java.util.UUID

import eie.io._
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import pipelines.Pipeline
import pipelines.reactive.DataSource.PushSource
import pipelines.reactive.PipelineRestService.Settings
import pipelines.socket.AddressedTextMessage

import scala.collection.concurrent
import scala.concurrent.Future

object PipelineRestService {
  case class Settings(persistUnder: Path, ioScheduler: Scheduler)

  def apply(ioScheduler: Scheduler): PipelineRestService = {
    apply(Settings("./data".asPath, ioScheduler))
  }

  def apply(settings: Settings): PipelineRestService = {
    import settings._
    val underlying = PipelineService()(ioScheduler)

    underlying.triggers.addTransform("persisted", Transform.writeToZipped(persistUnder))
    underlying.sinks.add(DataSink.count(Map("name"       -> "count")))
    underlying.sinks.add(RegisterAsSourceSink(Map("name" -> "register", "prefix" -> "register"), underlying.sources))

    new PipelineRestService(settings, underlying, ioScheduler)
  }
}

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
  def pipelines(): concurrent.Map[UUID, Pipeline[_]] = underlying.pipelines

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
              callback: TriggerCallback = Ignore): Future[Ack] = {
    underlying.triggers.connect(sourceCriteria, sinkCriteria, transforms, false, callback)
  }

  /**
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
                                     callback: TriggerCallback = Ignore): Either[String, (Transform, Future[Ack])] = {
    underlying.sources.find(criteria) match {
      case Seq(only) =>
        val newTransform = Transform.combineLatest(only)
        val ack          = underlying.triggers.addTransform(name, newTransform, replace, callback)
        Right(newTransform -> ack)
      case Seq() =>
        underlying.sources.list() match {
          case Seq()     => Left(s"There are no registered sources to match")
          case Seq(only) => Left(s"The source '${only.metadata.mkString("[", ",", "]")}' doesn't match")
          case Seq(_, _) => Left(s"Neither of the 2 sources match")
          case many      => Left(s"None of the ${many.size} sources match")
        }
      case many => Left(s"${many.size} of the ${underlying.sources.size} sources match")
    }
  }

  def getOrCreateDump(text: String) = underlying.triggers.addTransform(s"dump $text", Transform.dump(text))

  def getOrCreatePushSource(metadata: Map[String, String], callback: TriggerCallback = Ignore): (DataSource, Future[Ack]) = {
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
  def getOrCreateStringSource(content: String, metadata: Map[String, String], callback: TriggerCallback = Ignore): (DataSource, Future[Ack]) = {
    val source = DataSource[String](Observable(content)).addMetadata(metadata)
    underlying.sources.add(source, callback)
  }

}
