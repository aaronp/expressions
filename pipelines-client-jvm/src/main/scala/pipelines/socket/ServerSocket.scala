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
class ServerSocket private (val toRemote: Observer[AddressedMessage],
                            val fromRemote: Observable[AddressedMessage],
                            val toClientInput: Observer[AddressedMessage],
                            val output: Observable[AddressedMessage],
                            val scheduler: Scheduler) {

  def withOutput(newOutput: Observable[AddressedMessage]) = {
    new ServerSocket(toRemote, fromRemote, toClientInput, newOutput, scheduler)
  }

  lazy val akkaSink: Sink[Message, _]                = asAkkaSink("\t!\tServerSocket sink", toClientInput)(scheduler)
  lazy val akkaSource: Source[Message, NotUsed]      = asAkkaSource(s"\t!\tServerSocket src", output)(scheduler)
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

object ServerSocket {

  def apply(scheduler: Scheduler): ServerSocket = {
    apply(SocketSettings("anon"))(scheduler)
  }

  def apply(settings: SocketSettings)(implicit scheduler: Scheduler): ServerSocket = {
    import settings._

    val (toClientInput: Observer[AddressedMessage], toClientOutput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"$id-input", settings.input)
    }

    val (fromClientInput: Observer[AddressedMessage], fromClientOutput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"$id-output", settings.output)
    }

    new ServerSocket(fromClientInput, toClientOutput, toClientInput, fromClientOutput, scheduler)
  }
}
