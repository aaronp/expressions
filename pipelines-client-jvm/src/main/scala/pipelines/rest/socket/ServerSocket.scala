package pipelines.rest.socket

import java.util.UUID
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
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
  * @param toClient the channel for sending messages to the remote connection
  * @param fromRemoteOutput the channel of messages coming from the remote connection
  * @param toRemoteAkkaInput the input to the akka sink which drives the akka sink -- the other end of the 'toRemote' pipe
  * @param toClientAkkaInput the output of the akka source which drives data into the 'fromRemote' observable
  * @param scheduler
  */
final class ServerSocket private (val toClient: Observer[AddressedMessage],
                                  val toClientAkkaInput: Observable[AddressedMessage],
                                  val toRemoteAkkaInput: Observer[AddressedMessage],
                                  val fromRemoteOutput: Observable[AddressedMessage],
                                  val scheduler: Scheduler)
    extends StrictLogging {

  def sendToClient[T: ClassTag: Encoder](value: T): Future[Ack] = {
    toClient.onNext(AddressedMessage(value))
  }

  val akkaSink: Sink[Message, _]                = asAkkaSink("\t!\tServerSocket sink", toRemoteAkkaInput)(scheduler)
  val akkaSource: Source[Message, NotUsed]      = asAkkaSource(s"\t!\tServerSocket src", toClientAkkaInput)(scheduler)
  val akkaFlow: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSource(akkaSink, akkaSource)

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

    logger.info(s"Registering new socket connection from ${user} w/ id '${commonId}'")

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
    for {
      (_, newSource) <- sourceFuture
      (_, newSink)   <- sinkFuture
    } yield {
      val handshake = SocketConnectionAck(commonId, newSource.metadata, newSink.metadata, user)
      logger.info(s"Sending ack on new socket: $handshake")
      socket.sendToClient(handshake)

      val clientAcks: Observable[SocketClientConnectionAck] = socket.fromRemoteOutput.flatMap { fromClient =>
        logger.info(s"""Got a client addressed messsage: $fromClient""".stripMargin)
        Observable.fromIterable(fromClient.as[SocketClientConnectionAck].toOption)
      }

      // respond to client ack
      clientAcks.foreach { clientAck =>
        logger.info(s"""Sending client a handshake in response to a $clientAck""".stripMargin)
        socket.sendToClient(handshake)
      }

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

    val (toClient: Observer[AddressedMessage], toClientAkkaInput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"$name-output", settings.output)
    }

    val (fromClientAkkaInput: Observer[AddressedMessage], fromClient: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"$name-input", settings.input)
    }

    new ServerSocket(toClient, toClientAkkaInput, fromClientAkkaInput, fromClient, scheduler)
  }
}
