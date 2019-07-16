package pipelines.server

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.StrictLogging
import monix.execution.Ack
import pipelines.reactive._
import pipelines.reactive.trigger.Trigger
import pipelines.rest.socket.{AddressedMessage, SocketSubscribeRequest, SocketSubscribeResponse, SocketUnsubscribeRequest}

import scala.concurrent.Future
import scala.util.Success

/**
  * Create a DataSink which, upon receiving 'subscribe' and 'unsubscribe' messages will register triggers which
  * will connect sources with the socket sink.
  *
  * So, the workflow looks like this:
  *
  * 1) a client logs in and gets a JWT auth token
  * 2) the client uses that auth token for normal REST calls, one REST call being to create a temporary, single-use
  *    'socket' token to use as the protocol when creating a wss socket.
  * 3) the client then hits the GET request to upgrade to a web socket having specified the temp token as the protocol
  * 4) the server looks up the JWT auth token from that temp connect token and creates a ServerSocket, and registers a
  *    new SocketSource and SocketSink
  * 5) The registry of a new socket source (and sink) invoke the matching triggers (See PipelineService.triggers), of
  *    which this is one .... we'll then get the AddressedMessages which contain subscribe/unsubscribe requests.
  *
  *
  * Subscribing/Unsubscribing
  *
  * When we get a subscription, we find the source(s) indicated by the matching criteria and subscribe to those with
  * a new DataSink which will send the messages to the existing SocketSink. An ID is remembered which track that subscription
  * so subsequent unsubscribe calls can kill it.
  *
  */
private[server] class SubscribeOnMatchSink(pipelinesService: PipelineService) extends StrictLogging {

  private def listenerMetadata          = Map(tags.SinkType -> tags.typeValues.SubscriptionListener)
  private val pipelinesBySubscriptionId = new ConcurrentHashMap[String, UUID]()

  /**
    * A consumer of new SocketSource DataSources which will listen for [[AddressedMessage]]s for SocketSubscribeRequest
    * and SocketUnsubscribeRequest.
    */
  val addressedMessageRoutingSink: DataSink.Instance[AddressedMessage, Unit] = DataSink
    .foreach[AddressedMessage](listenerMetadata) { msg: AddressedMessage =>
      val text = s"\taddressedMessageRoutingSink.foreach -- $msg"
      logger.info(text)

      msg.as[SocketSubscribeRequest] match {
        case Success(request) => onSocketSubscribe(request)
        case _ =>
          val unsubscribeTry = msg.as[SocketUnsubscribeRequest]
          unsubscribeTry.foreach { unsubscribe: SocketUnsubscribeRequest =>
            onUnsubscribe(unsubscribe)
          }
      }
    }

  private def onUnsubscribe(unsubscribe: SocketUnsubscribeRequest): Unit = {
    val matchIdOpt = Option(pipelinesBySubscriptionId.remove(unsubscribe.addressedMessageId))
    matchIdOpt match {
      case None =>
        logger.warn(s"Couldn't unsubscribe from unknown subscription '${unsubscribe.addressedMessageId}'")
      case Some(matchId) =>
        val success = pipelinesService.cancel(matchId).isDefined
        if (success) {
          logger.info(s"Unsubscribed from socket '${unsubscribe.addressedMessageId}' cancelling '${matchId}'")
        } else {
          logger.warn(s"Unsubscribe FAILED from socket '${unsubscribe.addressedMessageId}' cancelling '${matchId}'")
        }
    }
  }
  private def onSocketSubscribe(request: SocketSubscribeRequest): Future[Ack] = {
    val SocketSubscribeRequest(_, _, subscriptionId, transforms, retainAfterMatch) = request
    val trigger                                                  = Trigger(request.sourceAsCriteria, request.sinkAsCriteria, transforms)

    val callback = TriggerCallback.PromiseCallback()

    import pipelinesService.sources.scheduler

    callback.matchFuture.foreach {
      case (pMatch, pipeline) =>
        logger.debug(s"Adding pipeline subscription ${subscriptionId} to ${pMatch.matchId}")

        // TODO - verify we're not clobbering an existing subscription ID!
        // We could do it now, but there's a bigger piece about verification of what/who can connect sources and sinks
        pipelinesBySubscriptionId.put(subscriptionId, pMatch.matchId)
        val response = SocketSubscribeResponse(Option(pMatch.matchId.toString), pipeline.sink.id.toSeq)
        Success(response)
    }
    pipelinesService.triggers.connect(trigger, retainAfterMatch, callback)
  }
}

object SubscribeOnMatchSink {

  /**
    * The client will subscribe to all new 'SocketSource' sources created (when clients open a websocket) and listen
    * for
    *
    * @param pipelinesService
    * @return
    */
  def listenToNewSocketSources(pipelinesService: PipelineService): Unit = {

    val subscribeOnMatchSink                   = new SubscribeOnMatchSink(pipelinesService)
    val (listenForSubscriptionMessagesSink, _) = pipelinesService.sinks.add(subscribeOnMatchSink.addressedMessageRoutingSink)

    implicit val sched = pipelinesService.sources.scheduler
    // add a transform (a predicate in this case) for filtering the messages we want
    val isSocketMessageName = "isSocketMessage"
    pipelinesService.addTransform(isSocketMessageName, isSocketMessage).foreach { _ =>
      // now that the transform is added, add a trigger so that new socket sources will get consumed by this sink
      // which filters on socket subscribe/unsubscribe messages
      val trigger = Trigger(
        MetadataCriteria(Map(tags.SourceType -> tags.typeValues.Socket)),
        MetadataCriteria(Map(tags.Id         -> listenForSubscriptionMessagesSink.id.get)),
        Seq(isSocketMessageName)
      )
      pipelinesService.triggers.connect(trigger, true, TriggerCallback.Ignore)
    }
  }

  private[server] val isSocketMessage: Transform.FixedTransform[AddressedMessage, AddressedMessage] = {
    val subscribeRequestTopic   = AddressedMessage.topics.forClass[SocketSubscribeRequest]
    val unsubscribeRequestTopic = AddressedMessage.topics.forClass[SocketUnsubscribeRequest]
    Transform.filter[AddressedMessage] { msg =>
      (msg.to == subscribeRequestTopic ||
      msg.to == unsubscribeRequestTopic)
    }
  }
}
