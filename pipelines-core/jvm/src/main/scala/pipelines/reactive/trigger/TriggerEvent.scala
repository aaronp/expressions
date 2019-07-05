package pipelines.reactive.trigger

import java.util.UUID

import pipelines.reactive.{DataSink, DataSource, OnNewTrigger, Transform}

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
case class PipelineMatch(matchId: UUID, source: DataSource, transforms: Seq[(String, Transform)], sink: DataSink, trigger: OnNewTrigger) extends TriggerEvent {
  def typesMatch: Boolean = {
    val chainedSourceOpt = transforms.foldLeft(Option(source)) {
      case (None, _)           => None
      case (Some(src), (_, t)) => t.applyTo(src)
    }
    chainedSourceOpt.exists(_.contentType.matches(sink.inputType))
  }

  override def matches: Seq[PipelineMatch] = Seq(this)
}
object PipelineMatch {
  def apply(source: DataSource, transforms: Seq[(String, Transform)], sink: DataSink, trigger: OnNewTrigger): PipelineMatch = {
    new PipelineMatch(UUID.randomUUID, source, transforms, sink, trigger)
  }
}

/**
  * A new trigger matches multiple sources and sinks
  *
  * @param matches
  */
case class MultipleMatchesOnTrigger(override val matches: Seq[PipelineMatch]) extends TriggerEvent

case class MatchedSourceWithManySinks(dataSource: DataSource, transforms: Seq[(String, Transform)], sinks: Seq[DataSink], trigger: OnNewTrigger) extends TriggerEvent {
  override def matches: Seq[PipelineMatch] = {
    sinks.map { sink =>
      PipelineMatch(dataSource, transforms, sink, trigger)
    }
  }
}
case class MatchedSinkWithManySources(dataSources: Seq[DataSource], transforms: Seq[(String, Transform)], sink: DataSink, trigger: OnNewTrigger) extends TriggerEvent {
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
case class UnmatchedSource(dataSource: DataSource)         extends MismatchedMatch
case class UnmatchedSink(sink: DataSink)                   extends MismatchedMatch
case class UnmatchedTrigger(trigger: Trigger)              extends MismatchedMatch
case class MatchedSourceWithNoSink(dataSource: DataSource) extends MismatchedMatch

case class MatchedSourceWithMissingTransforms(dataSource: DataSource, missingTransform: String) extends MismatchedMatch
case class MatchedSinkWithNoSource(sink: DataSink)                                              extends MismatchedMatch
