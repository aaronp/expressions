package pipelines.reactive

import pipelines.reactive.trigger.Trigger
import pipelines.rest.socket.{AddressedMessage, EventDto}

sealed trait TriggerInput {
  def callback: TriggerCallback
}
sealed trait SourceEvent extends TriggerInput
object SourceEvent {
  def asAddressedMessage(event: SourceEvent): AddressedMessage = {
    event match {
      case source: OnSourceAdded =>
        val to = AddressedMessage.topics.forClass[OnSourceAdded]
        AddressedMessage(to, EventDto("OnSourceAdded", source.source.metadata))
      case source: OnSourceRemoved =>
        val to = AddressedMessage.topics.forClass[OnSourceRemoved]
        AddressedMessage(to, EventDto("OnSourceRemoved", source.source.metadata))
    }
  }
}

sealed trait SinkEvent extends TriggerInput
object SinkEvent {
  def asAddressedMessage(event: SinkEvent): AddressedMessage = {
    event match {
      case sink: OnSinkAdded =>
        val to = AddressedMessage.topics.forClass[OnSinkAdded]
        AddressedMessage(to, EventDto("OnSinkAdded", sink.sink.metadata))
      case sink: OnSinkRemoved =>
        val to = AddressedMessage.topics.forClass[OnSinkRemoved]
        AddressedMessage(to, EventDto("OnSinkRemoved", sink.sink.metadata))
    }
  }
}

case class OnSourceAdded(source: DataSource, override val callback: TriggerCallback)   extends SourceEvent
case class OnSourceRemoved(source: DataSource, override val callback: TriggerCallback) extends SourceEvent

case class OnSinkAdded(sink: DataSink, override val callback: TriggerCallback)   extends SinkEvent
case class OnSinkRemoved(sink: DataSink, override val callback: TriggerCallback) extends SinkEvent

case class OnNewTransform(name: String, transform: Transform, replace: Boolean, override val callback: TriggerCallback) extends TriggerInput
case class OnNewTrigger(trigger: Trigger, retainTriggerAfterMatch: Boolean, override val callback: TriggerCallback)     extends TriggerInput
