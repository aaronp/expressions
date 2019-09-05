package pipelines.rest.socket

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import monix.reactive.subjects.ConcurrentSubject
import pipelines.reactive._
import pipelines.users.Claims

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
  *
  */
final class AddressedMessageRouter() extends StrictLogging {

  private val listenerMetadata: Map[String, String] = Map(tags.SinkType -> tags.typeValues.AddressedMessageRouter)

  type Handler = (Claims, AddressedMessage) => Unit

  import scala.collection.JavaConverters._

  type HandlersById = ConcurrentHashMap[UUID, Handler]
  private val handlersByTo    = new ConcurrentHashMap[String, HandlersById]()
  private val generalHandlers = ListBuffer[Handler]()

  def addGeneralHandler(handler: Handler): AddressedMessageRouter = {
    generalHandlers += handler
    this
  }

  def addHandler[T: ClassTag](handler: Handler): UUID = {
    val to = AddressedMessage.topics.forClass[T]
    addHandler(to)(handler)
  }

  def addHandlerPipe[T: ClassTag: Decoder, R: ClassTag](responseChannel : Observer[R])(handler: (Claims, T) => R)(implicit s: Scheduler): UUID = {
    val to = AddressedMessage.topics.forClass[T]

    val handlerId = UUID.randomUUID()
    addHandler(to, handlerId) {
      case (user: Claims, msg) =>
        try {
          val input  = msg.as[T].get
          val result = handler(user, input)
          responseChannel.onNext(result)
        } catch {
          case NonFatal(e) =>
            if (removeHandler(to, handlerId)) {
              logger.error(s"Notifying handler pipe for '$to' of $e")
              responseChannel.onError(e)
            } else {
              logger.error(s"Received handler pipe error for '$to', but no handler existed for $handlerId: $e")
            }
        }
    }
  }

  def addHandler(to: String, key: UUID = UUID.randomUUID())(handler: Handler): UUID = {
    val byId: HandlersById = getOrCreateHandlerLookup(to)
    byId.computeIfAbsent(key, _ => handler)
    key
  }

  def removeHandler(to: String, key: UUID): Boolean = {
    Option(handlersByTo.get(to)).fold(false)(_.remove(key) != null)
  }

  private def createHandlers(key: String): HandlersById = {
    new ConcurrentHashMap[UUID, Handler]()
  }

  private def getOrCreateHandlerLookup(to: String): HandlersById = {
    handlersByTo.computeIfAbsent(to, createHandlers)
  }

  def onAddressedMessage(user: Claims, msg: AddressedMessage): Unit = {
    val text = s"\taddressedMessageRoutingSink.foreach -- $msg"
    logger.info(text)

    val found: Iterable[Handler] = handlersByTo.get(msg.to) match {
      case null                             => generalHandlers
      case byId if generalHandlers.nonEmpty => byId.asScala.values ++ generalHandlers
      case byId                             => byId.asScala.values
    }

    if (found.isEmpty) {
      logger.warn(s"Ignoring message $msg as no handlers found")
    } else {
      found.zipWithIndex.foreach {
        case (handler, i) =>
          logger.debug(s"Handling $i: '${msg.to}' w/ $handler")
          try {
            handler(user, msg)
          } catch {
            case NonFatal(e) => logger.error(s"Handler $i for '${msg.to}' failed with $e on: ${msg}", e)
          }
      }
    }
  }

  /**
    * A consumer of new SocketSource DataSources which will listen for [[AddressedMessage]]s and dispatch them to the
    * registered handlers
    */
  val addressedMessageRoutingSink: DataSink.Instance[(Claims, AddressedMessage), Unit] = DataSink
    .foreach[(Claims, AddressedMessage)](listenerMetadata) {
      case (user, msg) => onAddressedMessage(user, msg)
    }
}

object AddressedMessageRouter {

  def apply(): AddressedMessageRouter = new AddressedMessageRouter()
//  val (msgRouterSink, _) = pipelinesService.sinks.add(msgRouter.addressedMessageRoutingSink)
//
//  implicit val sched = pipelinesService.sources.scheduler
//  // add a transform (a predicate in this case) for filtering the messages we want
//  val isSocketMessageName = "isSocketSubscribeMessage"
//
//  pipelinesService.addTransform(isSocketMessageName, isSocketSubscribeMessage).foreach { _ =>
//    // now that the transform is added, add a trigger so that new socket sources will get consumed by this sink
//    // which filters on socket subscribe/unsubscribe messages
//    val trigger = Trigger(
//      MetadataCriteria(Map(tags.SourceType -> tags.typeValues.Socket)),
//      MetadataCriteria(Map(tags.Id         -> msgRouterSink.id.get)),
//      Seq(isSocketMessageName)
//    )
//    pipelinesService.triggers.connect(trigger, true, TriggerCallback.Ignore)
//  }
//  private[socket] val isSocketSubscribeMessage: Transform.FixedTransform[AddressedMessage, AddressedMessage] = {
//    val subscribeRequestTopic   = AddressedMessage.topics.forClass[SocketSubscribeRequest]
//    val unsubscribeRequestTopic = AddressedMessage.topics.forClass[SocketUnsubscribeRequest]
//    Transform.filter[AddressedMessage] { msg =>
//      (msg.to == subscribeRequestTopic ||
//      msg.to == unsubscribeRequestTopic)
//    }
//  }
}
