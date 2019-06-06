package pipelines.reactive.trigger

import pipelines.reactive.{DataSink, DataSource, Transform}

sealed trait TriggerEvent {
  def matches: Seq[PipelineMatch]
}

/**
  * This is the big daddy - the event which is propagated when a source and sink match
  *
  * @param source the source
  * @param transforms the transforms which should be applied
  * @param sink the matched sink
  * @param trigger the trigger which matched the source and sink
  */
case class PipelineMatch(source: DataSource, transforms: Seq[Transform], sink: DataSink, trigger: Trigger) extends TriggerEvent {
  def typesMatch: Boolean = {
    val chainedSourceOpt = transforms.foldLeft(Option(source)) {
      case (None, _)      => None
      case (Some(src), t) => t.applyTo(src)
    }
    chainedSourceOpt.exists(_.contentType.matches(sink.contentType))
  }

  override def matches: Seq[PipelineMatch] = Seq(this)
}

/**
  * A new trigger matches multiple sources and sinks
  *
  * @param matches
  */
case class MultipleMatchesOnTrigger(override val matches: Seq[PipelineMatch]) extends TriggerEvent

case class MatchedSourceWithManySinks(dataSource: DataSource, transforms: Seq[Transform], sinks: Seq[DataSink], trigger: Trigger) extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = {
    sinks.map { sink =>
      PipelineMatch(dataSource, transforms, sink, trigger)
    }
  }
}
case class MatchedSinkWithManySources(dataSources: Seq[DataSource], transforms: Seq[Transform], sink: DataSink, trigger: Trigger) extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = {
    dataSources.map { dataSource =>
      PipelineMatch(dataSource, transforms, sink, trigger)
    }
  }
}

case class TriggerAdded(trigger: Trigger) extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = Nil
}
case object NoOpTriggerEvent extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = Nil
}

sealed trait TransformEvent extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = Nil
}
case class TransformAdded(name: String)                                                             extends TransformEvent
case class TransformAlreadyExists(name: String, existing: Transform, attemptedTransform: Transform) extends TransformEvent

sealed trait MismatchedMatch extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = Nil
}
case class UnmatchedSource(dataSource: DataSource, availableTriggers: Seq[Trigger])                                        extends MismatchedMatch
case class UnmatchedSink(sink: DataSink, availableTriggers: Seq[Trigger])                                                  extends MismatchedMatch
case class UnmatchedTrigger(trigger: Trigger, availableSources: Seq[DataSource], availableSinks: Seq[DataSink])            extends MismatchedMatch
case class MatchedSourceWithNoSink(dataSource: DataSource, availableTriggers: Seq[Trigger], availableSinks: Seq[DataSink]) extends MismatchedMatch

case class MatchedSourceWithMissingTransforms(dataSource: DataSource, sink: DataSink, availableTriggers: Seq[Trigger], missingTransform: String) extends MismatchedMatch
case class MatchedSinkWithNoSource(sink: DataSink, availableTriggers: Seq[Trigger], availableSources: Seq[DataSource])                           extends MismatchedMatch
