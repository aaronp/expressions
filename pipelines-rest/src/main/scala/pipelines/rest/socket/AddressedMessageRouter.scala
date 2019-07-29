package pipelines.rest.socket

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.StrictLogging
import pipelines.reactive._
import pipelines.reactive.trigger.Trigger

import scala.reflect.ClassTag
import scala.util.control.NonFatal

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
  */
final class AddressedMessageRouter(val pipelinesService: PipelineService) extends StrictLogging {

  private def listenerMetadata = Map(tags.SinkType -> tags.typeValues.SubscriptionListener)

  type Handler = AddressedMessage => Unit

  import scala.collection.JavaConverters._

  type HandlersById = ConcurrentHashMap[UUID, Handler]
  private val handlersByTo = new ConcurrentHashMap[String, HandlersById]()

  def addHandler[T: ClassTag](handler: Handler): UUID = {
    val to = AddressedMessage.topics.forClass[T]
    addHandler(to)(handler)
  }
  def addHandler(to: String)(handler: Handler): UUID = {
    val byId: HandlersById = getOrCreateHandlerLookup(to)
    val key                = UUID.randomUUID()
    byId.computeIfAbsent(key, _ => handler)
    key
  }
  def removeHandler(to: String, key: UUID) = {
    Option(handlersByTo.get(to)).foreach(_.remove(key))
  }

  private def getOrCreateHandlerLookup(to: String): HandlersById = {
    def createHandlers(key: String): HandlersById = new ConcurrentHashMap[UUID, Handler]()
    handlersByTo.computeIfAbsent(to, createHandlers)
  }

  /**
    * A consumer of new SocketSource DataSources which will listen for [[AddressedMessage]]s for SocketSubscribeRequest
    * and SocketUnsubscribeRequest.
    */
  val addressedMessageRoutingSink: DataSink.Instance[AddressedMessage, Unit] = DataSink
    .foreach[AddressedMessage](listenerMetadata) { msg: AddressedMessage =>
      val text = s"\taddressedMessageRoutingSink.foreach -- $msg"
      logger.info(text)

      val handlers = handlersByTo.get(msg.to)
      if (handlers != null) {
        val found = handlers.asScala.values
        found.zipWithIndex.foreach {
          case (handler, i) =>
            logger.debug(s"Handling $i: '${msg.to}' w/ $handler")
            try {
              handler(msg)
            } catch {
              case NonFatal(e) =>
                logger.error(s"Handler $i for '${msg.to}' failed with $e on: ${msg}", e)
            }
        }
      } else {
        logger.warn(s"IGNORING MESSAGE addressed to '${msg.to}'")
      }
    }
}

object AddressedMessageRouter {

  /**
    * The client will subscribe to all new 'SocketSource' sources created (when clients open a websocket) and listen
    * for
    *
    * @param pipelinesService
    * @return
    */
  def apply(pipelinesService: PipelineService): AddressedMessageRouter = {

    val subscribeOnMatchSink                   = new AddressedMessageRouter(pipelinesService)
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
    subscribeOnMatchSink
  }

  private[socket] val isSocketMessage: Transform.FixedTransform[AddressedMessage, AddressedMessage] = {
    val subscribeRequestTopic   = AddressedMessage.topics.forClass[SocketSubscribeRequest]
    val unsubscribeRequestTopic = AddressedMessage.topics.forClass[SocketUnsubscribeRequest]
    Transform.filter[AddressedMessage] { msg =>
      (msg.to == subscribeRequestTopic ||
      msg.to == unsubscribeRequestTopic)
    }
  }
}
