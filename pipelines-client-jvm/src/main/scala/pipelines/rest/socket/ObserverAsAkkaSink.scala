package pipelines.rest.socket

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.Sink
import monix.execution.Scheduler
import monix.reactive.Observer

import scala.util.control.NonFatal

object ObserverAsAkkaSink {

  def apply(prefix: String, messages: Observer[AddressedMessage], scheduler: Scheduler): Sink[Message, NotUsed] = {
    val reactiveSub = new pipelines.rest.socket.WrappedPublisher.WrappedSubscriber[AddressedMessage](prefix, messages.toReactive(scheduler))

    Sink.fromSubscriber(reactiveSub).contramap[Message] { fromRemote: Message =>
      val addressed: AddressedMessage = try {
        asAddressedMessage(fromRemote)
      } catch {
        case NonFatal(e) => AddressedMessage.error(e.getMessage)
      }
      addressed
    }
  }
}
