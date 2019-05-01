package pipelines.socket

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Sink, Source}
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.{Observable, Observer}

/**
  * represents a pipe which can drive a web socket that can be subscribed to multiple sources
  *
  */
class ClientSocket private (private val fromServerInput: Observer[AddressedMessage],
                            val toClientOutput: Observable[AddressedMessage],
                            val toServerInput: Observer[AddressedMessage],
                            val toServerOutput: Observable[AddressedMessage],
                            val akkaSource: Source[Message, NotUsed],
                            val akkaSink: Sink[Message, _],
                            val scheduler: Scheduler) {

  lazy val akkaFlow: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSource(akkaSink, akkaSource)

  private val subscriptions: ConcurrentMap[UUID, Cancelable] = new ConcurrentHashMap[UUID, Cancelable]()
  def addClientSubscription(cancelable: Cancelable, id: UUID = UUID.randomUUID()): UUID = {
    subscriptions.putIfAbsent(id, cancelable)
    id
  }
  def getSubscription(id: UUID): Option[Cancelable] = {
    Option(subscriptions.get(id))
  }
  def cancelSubscription(id: UUID): Boolean = {
    Option(subscriptions.get(id)).fold(false) { c =>
      c.cancel()
      true
    }
  }
}
object ClientSocket {

  def apply(settings: SocketSettings)(implicit scheduler: Scheduler): ClientSocket = {
    import settings._

    val (fromServerInput: Observer[AddressedMessage], toClientOutput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"$id-input", settings.input)
    }

    val (toServerInput: Observer[AddressedMessage], toServerOutput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"$id-output", settings.output)
    }

    val akkaSink: Sink[Message, _]           = asAkkaSink(s"\t!c!\t$id-sink", fromServerInput)
    val akkaSource: Source[Message, NotUsed] = asAkkaSource(s"\t!c!\t$id-src", toServerOutput)

    new ClientSocket(fromServerInput, toClientOutput, toServerInput, toServerOutput, akkaSource, akkaSink, scheduler)
  }

}
