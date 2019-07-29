package pipelines.rest.socket

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.circe.{Decoder, Encoder}
import monix.execution.{Ack, Cancelable, CancelableFuture, Scheduler}
import monix.reactive.{Observable, Observer}

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * represents a pipe which can drive a web socket that can be subscribed to multiple sources
  *
  */
class ClientSocket private (private val fromServerInput: Observer[AddressedMessage],
                            val fromServer: Observable[AddressedMessage],
                            val toServerInput: Observer[AddressedMessage],
                            val toServerOutput: Observable[AddressedMessage],
                            val akkaSource: Source[Message, NotUsed],
                            val akkaSink: Sink[Message, _],
                            val scheduler: Scheduler) {

  lazy val akkaFlow: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSource(akkaSink, akkaSource)

  def expect[T: ClassTag: Decoder](n: Int = 1): CancelableFuture[List[T]] = {
    fromServer
      .flatMap {
        case msg: AddressedMessage => Observable.fromIterable(msg.as[T].toOption)
      }
      .take(n)
      .toListL
      .runToFuture(scheduler)
  }

  def requestHandshake(): Future[Ack] = send(SocketConnectionAckRequest(true))

  def send[T: ClassTag: Encoder](data: T): Future[Ack] = {
    sendAddressedMessage(AddressedMessage(data))
  }

  def sendAddressedMessage(data: AddressedMessage): Future[Ack] = {
    toServerInput.onNext(data)
  }

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
      PipeSettings.pipeForSettings(s"'${name}' input", settings.input)
    }

    val (toServerInput: Observer[AddressedMessage], toServerOutput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"'${name}' output", settings.output)
    }

    val akkaSink: Sink[Message, _]           = ObserverAsAkkaSink(s"\t!ClientSocket!\t$name-sink", fromServerInput, scheduler)
    val akkaSource: Source[Message, NotUsed] = ObservableAsAkkaSource(s"\t!ClientSocket!\t$name-src", toServerOutput, scheduler)

    new ClientSocket(fromServerInput, toClientOutput, toServerInput, toServerOutput, akkaSource, akkaSink, scheduler)
  }

}
