package pipelines.rest.socket

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Sink, Source}
import io.circe.Encoder
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.{Observable, Observer}
import pipelines.reactive.{PipelineService, tags}
import pipelines.users.Claims

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * represents a pipe which can drive a web socket that can be subscribed to multiple sources
  *
  * @param toServerFromRemote the channel for sending messages to the remote connection
  * @param fromRemoteOutput the channel of messages coming from the remote connection
  * @param toRemote the input to the akka sink which drives the akka sink -- the other end of the 'toRemote' pipe
  * @param toRemoteAkkaInput the output of the akka source which drives data into the 'fromRemote' observable
  * @param scheduler
  */
final class ServerSocket private (val toServerFromRemote: Observer[AddressedMessage],
                                  val toRemoteAkkaInput: Observable[AddressedMessage],
                                  val fromRemoteOutput: Observable[AddressedMessage],
                                  val toRemote: Observer[AddressedMessage],
                                  val scheduler: Scheduler) {

  def sendToClient[T: ClassTag: Encoder](value: T): Future[Ack] = {
    toServerFromRemote.onNext(AddressedMessage(value))
  }
  def withOutput(newOutput: Observable[AddressedMessage]): ServerSocket = {
    new ServerSocket(toServerFromRemote, newOutput, fromRemoteOutput, toRemote, scheduler)
  }

  lazy val akkaSink: Sink[Message, _]                = asAkkaSink("\t!\tServerSocket sink", toRemote)(scheduler)
  lazy val akkaSource: Source[Message, NotUsed]      = asAkkaSource(s"\t!\tServerSocket src", toRemoteAkkaInput)(scheduler)
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

  /**
    * What to do when a new WebSocket is opened? Register a source and sink!
    */
  final def register(user: Claims, queryMetadata: Map[String, String], pipelinesService: PipelineService): Future[(SocketSource, SocketConnectionAck, SocketSink)] = {
    val socket = this

    val name    = queryMetadata.getOrElse(tags.Name, s"socket-${user.userId}")
    val persist = queryMetadata.getOrElse(tags.Persist, false.toString).toBoolean

    // use the same ID for both the source and sink so we can more easily associate them when needed.
    val commonId: String = UUID.randomUUID.toString

    val baseMetadata: Map[String, String] = Map( //
                                                tags.Id          -> commonId, //
                                                tags.ContentType -> SocketSource.contentType.toString, //
                                                tags.Name        -> name) ++ queryMetadata

    val sourceFuture: Future[(Boolean, SocketSource)] = pipelinesService.getOrCreateSourceForName(name, true, persist) {
      val sourceMetadata = baseMetadata.updated(tags.SourceType, tags.typeValues.Socket)
      SocketSource(user, socket, sourceMetadata)
    }
    val sinkFuture: Future[(Boolean, SocketSink)] = pipelinesService.getOrCreateSinkForName(name, true, persist) {
      val sinkMetadata = baseMetadata.updated(tags.SinkType, tags.typeValues.Socket)
      SocketSink(user, socket, sinkMetadata)
    }

    implicit val s = scheduler
    /**
      * Send our first message over the new socket - a [[SocketConnectionAck]] which can be used so subsequently subscribe to sources
      * (via a [[SocketSubscribeRequest]]) using the id/source/sink metadata from the ack.
      *
      */
    import pipelinesService.sources.scheduler
    for {
      (_, newSource) <- sourceFuture
      (_, newSink)   <- sinkFuture
    } yield {
      val handshake = SocketConnectionAck(commonId, newSource.metadata, newSink.metadata, user)
      socket.sendToClient(handshake)
      (newSource, handshake, newSink)
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

    new ServerSocket(fromClientInput, fromClientOutput, toClientOutput, toClientInput, scheduler)
  }
}
