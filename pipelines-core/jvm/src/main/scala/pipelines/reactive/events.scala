package pipelines.reactive

import pipelines.reactive.trigger.Trigger

sealed trait TriggerInput
sealed trait SourceEvent extends TriggerInput
sealed trait SinkEvent   extends TriggerInput

case class OnSourceAdded(source: DataSource)     extends SourceEvent
case class OnSourceRemoved(source: DataSource) extends SourceEvent

case class OnSinkAdded(sink: DataSink)     extends SinkEvent
case class OnSinkRemoved(sink: DataSink) extends SinkEvent

case class OnNewTransform(name: String, transform: Transform, replace: Boolean) extends TriggerInput
case class OnNewTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean)     extends TriggerInput
