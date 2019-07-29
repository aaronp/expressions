package pipelines.rest.socket

import java.util.UUID

import io.circe.{Decoder, Encoder}
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import pipelines.reactive.tags

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/**
  * common class to expose some plumbing common to scalaJS or JVM clients
  *
  * @param scheduler
  */
abstract class ClientSocketSessionState(val scheduler: Scheduler) {

  def connectionAck: CancelableFuture[Try[SocketConnectionAck]] = {
    observerOf[SocketConnectionAck].headL.runToFuture(scheduler)
  }

  def messages: Observable[AddressedMessage]

  protected def logInfo(msg: String): Unit

  protected def raiseError(msg: String): Unit

  protected def sendMessage(msg: AddressedMessage): Unit

  def subscribeToSource(sourceId: String, transforms: Seq[String] = Nil, subscriptionId: String = UUID.randomUUID.toString, retainAfterMatch: Boolean = false) = {
    connectionAck.foreach {
      case Success(ack: SocketConnectionAck) =>
        val request = ack.subscribeToSource(sourceId, transforms, subscriptionId, retainAfterMatch)
//        logInfo(s"Client got connection ack, our source/sink id is ${ack.commonId}")
        send(request)
      case Failure(err) =>
        raiseError(s"Failure getting socket ack: $err")
    }(scheduler)
    subscriptionId
  }

  def subscribeToSourceEvents() = {
    subscribe(Map(tags.Label -> tags.labelValues.SourceEvents), Seq(tags.transforms.`SourceEvent.asAddressedMessage`), retainAfterMatch = true)
  }
  def subscribeToSinkEvents() = {
    subscribe(Map(tags.Label -> tags.labelValues.SinkEvents), Seq(tags.transforms.`SinkEvent.asAddressedMessage`), retainAfterMatch = true)
  }

  def subscribe(sourceCriteria: Map[String, String],
                transforms: Seq[String] = Nil,
                subscriptionId: String = UUID.randomUUID.toString,
                retainAfterMatch: Boolean = false): String = {
    connectionAck.foreach {
      case Success(ack: SocketConnectionAck) =>
        logInfo(s"Subscribing w/ $subscriptionId to socket ${ack.commonId}")
        val request = ack.subscribeTo(sourceCriteria, transforms, subscriptionId, retainAfterMatch)
        send(request)
      case Failure(err) =>
        raiseError(s"Failure getting socket ack: $err")
    }(scheduler)
    subscriptionId
  }

  def unSubscribe(subscriptionId: String): Unit = {
    logInfo(s"Unsubscribing w/ $subscriptionId from socket")
    send(SocketUnsubscribeRequest(subscriptionId))
  }

  def send[T: ClassTag: Encoder](data: T): Unit = {
    val msg: AddressedMessage = AddressedMessage(data)
    sendMessage(msg)
  }

  /**
    * Convenience to filter [[AddressedMessage]]s coming through the web socket on a particular 'to' topic
    * which corresponds to a classname, as well as trying to unmarshal the body of the message as the 'T' type
    *
    * @tparam T
    * @return a stream of 'T' messages
    */
  def observerOf[T: ClassTag: Decoder]: Observable[Try[T]] = {
    val topicName = AddressedMessage.topics.forClass[T]
    messages.filter(_.to == topicName).map(_.as[T])
  }

//  def sendMessage(data: AddressedMessage): Unit = {
//    import io.circe.syntax._
//    logInfo(s"sendMessage(${data})")
////    socket.send(data.asJson.noSpaces)
//    sendJson(data.asJson.noSpaces)
//  }

//  def close(code: Int = 0): Unit = socket.close(code)

}
