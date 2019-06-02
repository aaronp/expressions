package pipelines.reactive.trigger

import pipelines.reactive.{DataSink, DataSource, Transform}

/**
  *
  * @param transformsByName
  * @param triggers
  * @param sources
  * @param sinks
  */
case class TriggerState private[trigger] (
    transformsByName: Map[String, Transform],
    triggers: Seq[Trigger],
    sources: Seq[DataSource],
    sinks: Seq[DataSink]
) {

  override def toString: String = {
    s"Triggers(${transformsByName.size} transforms:${transformsByName.keySet.mkString(",")}, ${triggers.size} triggers, ${sources.size} sources, ${sinks.size} sinks)"
  }

  def onAddTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean): (TriggerState, TriggerEvent) = {
    val newState = if (retainTriggerAfterMatch) {
      copy(triggers = trigger +: triggers)
    } else {
      this
    }
    newState -> TriggerAdded(trigger)
  }

  def onAddTransform(name: String, transform: Transform, replace: Boolean): (TriggerState, TriggerEvent) = {
    transformsByName.get(name) match {
      case None =>
        copy(transformsByName = transformsByName.updated(name, transform)) -> TransformAdded(name)
      case Some(_) if replace =>
        copy(transformsByName = transformsByName.updated(name, transform)) -> TransformAdded(name)
      case Some(old) =>
        copy(transformsByName = transformsByName.updated(name, transform)) -> TransformAlreadyExists(name, old, transform)
    }
  }

  def onSink(dataSink: DataSink): (TriggerState, TriggerEvent) = {
    val allSinks = dataSink +: sinks
    val matchedSinks = triggers.flatMap { trigger =>
      allSinks.flatMap { sink =>
        if (trigger.matchesSink(sink)) {
          Option(sink -> trigger)
        } else {
          None
        }
      }
    }

    val event: TriggerEvent = if (matchedSinks.isEmpty) {
      UnmatchedSink(dataSink, triggers)
    } else {
      val allMatches: Seq[(DataSource, DataSink, Trigger)] = sources.flatMap { source =>
        matchedSinks.collect {
          case (sink, trigger) if trigger.matchesSource(source) => (source, sink, trigger)
        }
      }

      resolveMatches(allMatches, MatchedSinkWithNoSource(dataSink, triggers, sources)) { all =>
        val validSources               = all.map(_.source)
        val transforms: Seq[Transform] = all.head.transforms
        MatchedSinkWithManySources(validSources, transforms, dataSink)
      }
    }

    copy(sinks = allSinks) -> event
  }
  def onSource(dataSource: DataSource): (TriggerState, TriggerEvent) = {
    val allSources = dataSource +: sources
    val matchedSources: Seq[(DataSource, Trigger)] = triggers.flatMap { trigger =>
      allSources.flatMap { source =>
        if (trigger.matchesSource(source)) {
          Option(source -> trigger)
        } else {
          None
        }
      }
    }

    val event: TriggerEvent = if (matchedSources.isEmpty) {
      UnmatchedSource(dataSource, triggers)
    } else {
      val allMatches: Seq[(DataSource, DataSink, Trigger)] = sinks.flatMap { sink =>
        matchedSources.collect {
          case (source, trigger) if trigger.matchesSink(sink) => (source, sink, trigger)
        }
      }
      resolveMatches(allMatches, MatchedSourceWithNoSink(dataSource, triggers, sinks)) { all =>
        val validSinks                 = all.map(_.sink)
        val transforms: Seq[Transform] = all.head.transforms
        MatchedSourceWithManySinks(dataSource, transforms, validSinks)
      }
    }

    copy(sources = allSources) -> event
  }

  /** try and find all matches for the current source and sink
    *
    * @return the matches w/ the current sources, sinks and transforms
    */
  def triggerMatch(): Seq[PipelineMatch] = {
    triggers.flatMap { trigger =>
      sources.flatMap {
        case source if trigger.matchesSource(source) =>
          val nested: Seq[Option[PipelineMatch]] = sinks.collect {
            case sink if trigger.matchesSink(sink) =>
              resolveTransformations(source, sink, trigger) match {
                case ok: PipelineMatch => Some(ok)
                case _                       => None
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
  private def resolveMatches(allMatches: Seq[(DataSource, DataSink, Trigger)], onError: => TriggerEvent)(
      multiMatchAsEvent: Seq[PipelineMatch] => TriggerEvent): TriggerEvent = {
    allMatches match {
      case Seq()                     => onError
      case Seq((src, sink, trigger)) => resolveTransformations(src, sink, trigger)
      case many @ firstSinkMatch +: _ =>
        val resolved = many.flatMap {
          case (src, sink, trigger) =>
            resolveTransformations(src, sink, trigger) match {
              case unique: PipelineMatch => Option(unique)
              case _                           => None
            }
        }
        resolved match {
          case Seq()            => resolveTransformations(firstSinkMatch._1, firstSinkMatch._2, firstSinkMatch._3)
          case all @ first +: _ => multiMatchAsEvent(all)
        }
    }
  }

  private def resolveTransformations(dataSource: DataSource, sink: DataSink, trigger: Trigger): TriggerEvent = {
    // go through all the specified transforms and return either the resolved transformations or the Some of the first missing one
    val (missingTransformOpt, transforms) = trigger.transforms.foldLeft((Option.empty[String], Seq[Transform]())) {
      case ((None, transforms), transformKey) =>
        transformsByName.get(transformKey) match {
          case None            => (Some(transformKey), Nil)
          case Some(transform) => (None, transforms :+ transform)
        }
      case (entry, _) => entry
    }

    missingTransformOpt match {
      case None          => PipelineMatch(dataSource, transforms, sink, trigger)
      case Some(missing) => MatchedSourceWithMissingTransforms(dataSource, sink, triggers, missing)
    }
  }

  final def update(input: TriggerInput): (TriggerState, TriggerEvent) = {
    input match {
      case OnNewSource(source)                            => onSource(source)
      case OnNewSink(sink)                                => onSink(sink)
      case OnNewTransform(name, transform, replace)       => onAddTransform(name, transform, replace)
      case OnNewTrigger(trigger, retainTriggerAfterMatch) => onAddTrigger(trigger, retainTriggerAfterMatch)
    }
  }
}
