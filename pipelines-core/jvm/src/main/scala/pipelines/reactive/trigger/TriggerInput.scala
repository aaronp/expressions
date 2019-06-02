package pipelines.reactive.trigger

import pipelines.reactive.{DataSink, DataSource, Transform}

private[trigger] sealed trait TriggerInput
private[trigger] case class OnNewSource(source: DataSource)                                      extends TriggerInput
private[trigger] case class OnNewSink(sink: DataSink)                                            extends TriggerInput
private[trigger] case class OnNewTransform(name: String, transform: Transform, replace: Boolean) extends TriggerInput
private[trigger] case class OnNewTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean)     extends TriggerInput
