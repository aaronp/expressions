package pipelines.rest.socket.handlers

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.StrictLogging
import pipelines.reactive.trigger.Trigger
import pipelines.reactive.{PipelineService, TriggerCallback}
import pipelines.rest.socket.{AddressedMessage, AddressedMessageRouter, SocketSubscribeRequest, SocketSubscribeResponse, SocketUnsubscribeRequest}

import scala.concurrent.Future

object SubscriptionHandler {
  def register(commandRouter: AddressedMessageRouter): SubscriptionHandler = {
    val subscriptionHandler: SubscriptionHandler = new SubscriptionHandler(commandRouter.pipelinesService)
    commandRouter.addHandler[SocketSubscribeRequest](subscriptionHandler.onSubscribeMsg)
    commandRouter.addHandler[SocketUnsubscribeRequest](subscriptionHandler.onUnsubscribeMsg)
    subscriptionHandler
  }
}

/**
  * An [[AddressedMessage]] handler which handles [[SocketSubscribeRequest]] and [[SocketUnsubscribeRequest]] which
  * connects sources with sinks.
  *
  * @param pipelinesService
  */
final class SubscriptionHandler(val pipelinesService: PipelineService) extends StrictLogging {
  private val pipelinesBySubscriptionId = new ConcurrentHashMap[String, UUID]()

  def onSubscribeMsg(msg: AddressedMessage): Unit = {
    val request = msg.as[SocketSubscribeRequest].get
    onSocketSubscribe(request)
  }

  def onUnsubscribeMsg(msg: AddressedMessage): Unit = {
    val request = msg.as[SocketUnsubscribeRequest].get
    onUnsubscribe(request)
  }

  def onUnsubscribe(unsubscribe: SocketUnsubscribeRequest): Boolean = {
    val matchIdOpt = Option(pipelinesBySubscriptionId.remove(unsubscribe.addressedMessageId))
    matchIdOpt match {
      case None =>
        logger.warn(s"Couldn't unsubscribe from unknown subscription '${unsubscribe.addressedMessageId}'")
        false
      case Some(matchId) =>
        val success = pipelinesService.cancel(matchId).isDefined
        if (success) {
          logger.info(s"Unsubscribed from socket '${unsubscribe.addressedMessageId}' cancelling '${matchId}'")
        } else {
          logger.warn(s"Unsubscribe FAILED from socket '${unsubscribe.addressedMessageId}' cancelling '${matchId}'")
        }
        success
    }
  }
  def onSocketSubscribe(request: SocketSubscribeRequest): Future[SocketSubscribeResponse] = {
    val SocketSubscribeRequest(_, _, subscriptionId, transforms, retainAfterMatch) = request
    val trigger                                                                    = Trigger(request.sourceAsCriteria, request.sinkAsCriteria, transforms)

    val callback = TriggerCallback.PromiseCallback()

    import pipelinesService.sources.scheduler

    val resultFuture: Future[SocketSubscribeResponse] = callback.matchFuture.map {
      case (pMatch, pipeline) =>
        logger.debug(s"Adding pipeline subscription ${subscriptionId} to ${pMatch.matchId}")

        // TODO - verify we're not clobbering an existing subscription ID!
        // We could do it now, but there's a bigger piece about verification of what/who can connect sources and sinks
        pipelinesBySubscriptionId.put(subscriptionId, pMatch.matchId)
        val response = SocketSubscribeResponse(Option(pMatch.matchId.toString), pipeline.sink.id.toSeq)
        response
    }
    pipelinesService.triggers.connect(trigger, retainAfterMatch, callback).flatMap { _ =>
      resultFuture
    }
  }

}
