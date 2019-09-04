package pipelines.rest.socket

import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer}
import pipelines.reactive.{Ids, PipelineService, tags}
import pipelines.users.Claims

import scala.concurrent.Future
import scala.reflect.ClassTag

/**
  * represents a pipe which can drive a web socket that can be subscribed to multiple sources
  *
  * @param toClient the channel for sending messages to the remote connection
  * @param dataFromClientOutput the channel of messages coming from the remote connection
  * @param dataFromClientInput the data received from the client by the akka sink will serve as an input into this Observer
  * @param toClientAkkaInput the output of the akka source which drives data into the 'fromRemote' observable
  * @param scheduler
  */
final class ServerSocket private (val toClient: Observer[AddressedMessage],
                                  val toClientAkkaInput: Observable[AddressedMessage],
                                  val dataFromClientInput: Observer[AddressedMessage],
                                  val dataFromClientOutput: Observable[AddressedMessage],
                                  val scheduler: Scheduler,
                                  leaveSinkOpen: Boolean,
                                  leaveSourceOpen: Boolean)
    extends StrictLogging {

  def sendToClient[T: ClassTag: Encoder](value: T): Future[Ack] = {
    toClient.onNext(AddressedMessage(value))
  }

  val akkaSink: Sink[Message, _]                = ObserverAsAkkaSink("\t!\tServerSocket.akkaSink", dataFromClientInput, scheduler, leaveSinkOpen)
  val akkaSource: Source[Message, NotUsed]      = ObservableAsAkkaSource(s"\t!\tServerSocket.akkaSource", toClientAkkaInput, scheduler, leaveSourceOpen)
  val akkaFlow: Flow[Message, Message, NotUsed] = Flow.fromSinkAndSource(akkaSink, akkaSource)

  /**
    * A new WebSocket has been opened, so register it as a new source and sink
    *
    * This 'register' procedure sets up a Once created, a 'SocketConnectionAck' will be sent over the ServerSocket.
    *
    * @param user the authenticated user who opened the socket
    * @param queryMetadata any query params, which should be added to the 'metadata' for the new source/sink
    * @param pipelinesService the pipelines service against which the sources and sinks will be registered
    * @param commandRouter a router which will handle (route) messages received
    * @return a future of the registered source, ack and sink
    */
  final def register(user: Claims,
                     queryMetadata: Map[String, String],
                     pipelinesService: PipelineService,
                     commandRouter: AddressedMessageRouter): Future[(SocketSource, SocketConnectionAck, SocketSink)] = {
    val socket = this

    implicit val s = scheduler

    val name    = queryMetadata.getOrElse(tags.Name, s"socket for user ${user.name}")
    val persist = queryMetadata.getOrElse(tags.Persist, false.toString).toBoolean

    // use the same ID for both the source and sink so we can more easily associate them when needed.
    val commonId: String = s"svrSkt-${Ids.next()}"

    logger.info(s"Registering new socket connection from ${user} w/ id '${commonId}'")

    val baseMetadata: Map[String, String] = Map( //
                                                tags.Id          -> commonId, //
                                                tags.ContentType -> SocketSource.contentType.toString, //
                                                tags.Name        -> name) ++ queryMetadata

    val sourceFuture: Future[(Boolean, SocketSource)] = pipelinesService.getOrCreateSourceForName(name, true, persist) {
      val sourceMetadata = baseMetadata.updated(tags.SourceType, tags.typeValues.Socket)
      SocketSource(user, socket, sourceMetadata)
    }

    /**
      * Consume the data coming through the socket with the command router
      */
    val sourceHandlerFuture: Future[SocketSource] = sourceFuture.map {
      case (true, newSocketSource) => newSocketSource.handleWith(commandRouter)
      case (false, socket) =>
        logger.warn(s"A socket source was already found for '$name'")
        socket
    }

    val sinkFuture: Future[(Boolean, SocketSink)] = pipelinesService.getOrCreateSinkForName(name, true, persist) {
      val sinkMetadata = baseMetadata.updated(tags.SinkType, tags.typeValues.Socket)
      SocketSink(user, socket, sinkMetadata)
    }

    /**
      * Send our first message over the new socket - a [[SocketConnectionAck]] which can be used so subsequently subscribe to sources
      * (via a [[SocketSubscribeRequest]]) using the id/source/sink metadata from the ack.
      *
      */
    for {
      newSource              <- sourceHandlerFuture
      (sinkCreated, newSink) <- sinkFuture
    } yield {
      if (!sinkCreated) {
        logger.warn(s"A socket sink was already found when one should've been created for '$name'")
      }

      val handshake = SocketConnectionAck(commonId, newSource.metadata, newSink.metadata, user)
      logger.info(s"Sending ack on new socket: $handshake")
      socket.sendToClient(handshake)

      val clientAcks: Observable[SocketConnectionAckRequest] = socket.dataFromClientOutput.flatMap { fromClient =>
        logger.info(s"""Got a client addressed message: $fromClient""".stripMargin)
        Observable.fromIterable(fromClient.as[SocketConnectionAckRequest].toOption)
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
    apply(SocketSettings("ServerSocket.apply"))(scheduler)
  }

  def apply(settings: SocketSettings)(implicit scheduler: Scheduler): ServerSocket = {
    import settings._

    val (toClient: Observer[AddressedMessage], toClientAkkaInput: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"ServerSocket '$name' output", settings.output)
    }

    val (fromClientAkkaInput: Observer[AddressedMessage], fromClient: Observable[AddressedMessage]) = {
      PipeSettings.pipeForSettings(s"ServerSocket '$name' input", settings.input)
    }


    new ServerSocket(toClient, toClientAkkaInput, fromClientAkkaInput, fromClient, scheduler, settings.leaveSourceOpen, settings.leaveSinkOpen)
  }
}
