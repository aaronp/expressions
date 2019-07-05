package pipelines.reactive.trigger

import java.util.UUID

import pipelines.reactive._

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  *
  * @param transformsByName
  * @param triggers
  * @param sources
  * @param sinks
  */
case class RepoState private[trigger] (
    transformsByName: Map[String, Transform],
    triggers: Seq[OnNewTrigger],
    sources: Seq[DataSource],
    sinks: Seq[DataSink]
) {

  lazy val sourcesById = sources.flatMap(s => s.id.map(_ -> s)).toMap

  override def toString: String = {
    s"Triggers(${transformsByName.size} transforms:${transformsByName.keySet.mkString(",")}, ${triggers.size} triggers, ${sources.size} sources, ${sinks.size} sinks)"
  }

  def onAddTrigger(event: OnNewTrigger): (RepoState, TriggerEvent) = {
    val newState = copy(triggers = event +: triggers)
    newState.triggerMatch() match {
      case Seq() if event.retainTriggerAfterMatch =>
        newState -> TriggerAdded(event.trigger)
      case Seq() =>
        this -> UnmatchedTrigger(event.trigger)
      case Seq(only) if event.retainTriggerAfterMatch =>
        newState -> only
      case Seq(only: PipelineMatch) =>
        this -> only
      case matches if event.retainTriggerAfterMatch =>
        newState -> MultipleMatchesOnTrigger(matches)
      case matches =>
        this -> MultipleMatchesOnTrigger(matches)
    }

  }

  def onAddTransform(name: String, transform: Transform, replace: Boolean): (RepoState, TriggerEvent) = {
    transformsByName.get(name) match {
      case None =>
        copy(transformsByName = transformsByName.updated(name, transform)) -> TransformAdded(name)
      case Some(_) if replace =>
        copy(transformsByName = transformsByName.updated(name, transform)) -> TransformAdded(name)
      case Some(old) =>
        copy(transformsByName = transformsByName.updated(name, transform)) -> TransformAlreadyExists(name, old, transform)
    }
  }

  def onSinkRemoved(dataSink: DataSink): RepoState = copy(sinks = sinks.filterNot(_ == dataSink))

  def onSinkAdded(dataSink: DataSink): (RepoState, TriggerEvent) = {
    val allSinks = dataSink +: sinks
    val matchedSinks = triggers.flatMap { triggerEvent =>
      allSinks.flatMap { sink =>
        if (triggerEvent.trigger.matchesSink(sink)) {
          Option(sink -> triggerEvent)
        } else {
          None
        }
      }
    }

    val event: TriggerEvent = if (matchedSinks.isEmpty) {
      UnmatchedSink(dataSink)
    } else {
      val allMatches: Seq[(DataSource, DataSink, OnNewTrigger)] = sources.flatMap { source =>
        matchedSinks.collect {
          case (sink, trigger) if trigger.trigger.matchesSource(source) => (source, sink, trigger)
        }
      }

      resolveMatches(allMatches, MatchedSinkWithNoSource(dataSink)) { all =>
        val validSources                         = all.map(_.source)
        val transforms: Seq[(String, Transform)] = all.head.transforms
        MatchedSinkWithManySources(validSources, transforms, dataSink, all.head.trigger)
      }
    }

    copy(sinks = allSinks) -> event
  }
  def onSourceRemoved(dataSource: DataSource) = copy(sources = sources.filterNot(_ == dataSource))

  def onSourceAdded(dataSource: DataSource): (RepoState, TriggerEvent) = {
    val allSources = dataSource +: sources
    val matchedSources: Seq[(DataSource, OnNewTrigger)] = triggers.flatMap { triggerEvent =>
      allSources.flatMap { source =>

        if (triggerEvent.trigger.matchesSource(source)) {
          Option(source -> triggerEvent)
        } else {
          None
        }
      }
    }

    val event: TriggerEvent = if (matchedSources.isEmpty) {
      UnmatchedSource(dataSource)
    } else {
      val allMatches: Seq[(DataSource, DataSink, OnNewTrigger)] = sinks.flatMap { sink =>
        matchedSources.collect {
          case (source, trigger) if trigger.trigger.matchesSink(sink) => (source, sink, trigger)
        }
      }
      resolveMatches(allMatches, MatchedSourceWithNoSink(dataSource)) { all =>
        val validSinks                           = all.map(_.sink)
        val transforms: Seq[(String, Transform)] = all.head.transforms
        MatchedSourceWithManySinks(dataSource, transforms, validSinks, all.head.trigger)
      }
    }

    copy(sources = allSources) -> event
  }

  /** try and find all matches for the current source and sink
    *
    * @return the matches w/ the current sources, sinks and transforms
    */
  def triggerMatch(): Seq[PipelineMatch] = {
    triggers.flatMap { triggerEvent =>
      sources.flatMap {
        case source if triggerEvent.trigger.matchesSource(source) =>
          val nested: Seq[Option[PipelineMatch]] = sinks.collect {
            case sink if triggerEvent.trigger.matchesSink(sink) =>
              resolveTransformations(source, sink, triggerEvent) match {
                case ok: PipelineMatch => Some(ok)
                case _                 => None
              }
          }
          nested.flatten
        case _ => Nil
      }
    }
  }

  /**
    * We have a many-to-one match between either one source and many sinks, or one sink and many sources.
    *
    * e.g., we're responding to either a new source event and trying to match sinks, or a new sink event and trying to match sources.
    *
    * This handler tries to resolve the source(s) -> transforms -> sink(s) chain to a single event -- either no match, one unique match,
    * or a multi-match
    *
    */
  private def resolveMatches(allMatches: Seq[(DataSource, DataSink, OnNewTrigger)], onError: => TriggerEvent)(multiMatchAsEvent: Seq[PipelineMatch] => TriggerEvent): TriggerEvent = {
    allMatches match {
      case Seq()                     => onError
      case Seq((src, sink, trigger)) => resolveTransformations(src, sink, trigger)
      case many @ firstSinkMatch +: _ =>
        val resolved = many.flatMap {
          case (src, sink, trigger) =>
            resolveTransformations(src, sink, trigger) match {
              case unique: PipelineMatch => Option(unique)
              case _                     => None
            }
        }
        resolved match {
          case Seq() => resolveTransformations(firstSinkMatch._1, firstSinkMatch._2, firstSinkMatch._3)
          case all   => multiMatchAsEvent(all)
        }
    }
  }

  private def resolveTransformations(dataSource: DataSource, sink: DataSink, trigger: OnNewTrigger): TriggerEvent = {
    // go through all the specified transforms and return either the resolved transformations or the Some of the first missing one
    val (missingTransformOpt, transforms) = trigger.trigger.transforms.foldLeft((Option.empty[String], Seq[(String, Transform)]())) {
      case ((None, transforms), transformKey) =>
        transformsByName.get(transformKey) match {
          case None            => (Some(transformKey), Nil)
          case Some(transform) => (None, transforms :+ (transformKey, transform))
        }
      case (entry, _) => entry
    }

    missingTransformOpt match {
      case None          => PipelineMatch(UUID.randomUUID, dataSource, transforms, sink, trigger)
      case Some(missing) => MatchedSourceWithMissingTransforms(dataSource, missing)
    }
  }

  final def update(input: TriggerInput): (RepoState, TriggerEvent) = {
    try {
      val result @ (_, event) = updateUnsafe(input)
      input.callback.onResult(Success(event))
      result
    } catch {
      case NonFatal(e) =>
        input.callback.onResult(Failure(e))
        throw e
    }
  }
  final def updateUnsafe(input: TriggerInput): (RepoState, TriggerEvent) = {
    input match {
      case OnSourceAdded(source, _)                    => onSourceAdded(source)
      case OnSourceRemoved(source, _)                  => onSourceRemoved(source) -> NoOpTriggerEvent
      case OnSinkAdded(sink, _)                        => onSinkAdded(sink)
      case OnSinkRemoved(sink, _)                      => onSinkRemoved(sink) -> NoOpTriggerEvent
      case OnNewTransform(name, transform, replace, _) => onAddTransform(name, transform, replace)
      case event @ OnNewTrigger(_, _, _)               => onAddTrigger(event)
    }
  }
}
