package pipelines.reactive

import pipelines.reactive.trigger.Trigger

sealed trait TriggerInput {
  def callback: TriggerCallback
}
sealed trait SourceEvent extends TriggerInput
sealed trait SinkEvent   extends TriggerInput

case class OnSourceAdded(source: DataSource, override val callback: TriggerCallback)   extends SourceEvent
case class OnSourceRemoved(source: DataSource, override val callback: TriggerCallback) extends SourceEvent

case class OnSinkAdded(sink: DataSink, override val callback: TriggerCallback)   extends SinkEvent
case class OnSinkRemoved(sink: DataSink, override val callback: TriggerCallback) extends SinkEvent

case class OnNewTransform(name: String, transform: Transform, replace: Boolean, override val callback: TriggerCallback) extends TriggerInput
case class OnNewTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean, override val callback: TriggerCallback)     extends TriggerInput
