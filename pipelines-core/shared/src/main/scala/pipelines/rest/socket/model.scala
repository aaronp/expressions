package pipelines.rest.socket

import java.util.UUID

import io.circe.{Decoder, ObjectEncoder}
import pipelines.reactive.{MetadataCriteria, tags}
import pipelines.users.Claims

/**
  * The first message which should be sent from the client.
  * This is a one-off heartbeat, which is replied to with a 'SocketConnectionAck'.
  *
  * In practice, any time this is sent the server should reply with a [[SocketConnectionAck]]
  */
final case class SocketConnectionAckRequest(fromTheClient: Boolean = true)
object SocketConnectionAckRequest {
  implicit val encoder: ObjectEncoder[SocketConnectionAckRequest] = io.circe.generic.semiauto.deriveEncoder[SocketConnectionAckRequest]
  implicit val decoder: Decoder[SocketConnectionAckRequest]       = io.circe.generic.semiauto.deriveDecoder[SocketConnectionAckRequest]
}

/**
  * The first messages sent when a socket connection has been made. It contains a 'commonId' -- an ID used for both sides
  * of the socket (SocketSource and SocketSink), and so can create [SocketSubscribeRequest] requests
  *
  * @param sourceMetadata
  * @param sinkMetadata
  */
final case class SocketConnectionAck(commonId: String, sourceMetadata: Map[String, String], sinkMetadata: Map[String, String], user: Claims) {

  /**
    * @param sourceId the ID of some DataSource to connect to
    * @param transforms any transforms to get the source type identified by 'sourceId' as an 'AddressedMessage'
    * @param subscriptionId a unique id which can be used in order to cancel the subscription
    * @return a request which can connect the sink for this websocket to the given source id via the specified transforms (provided the types match up)
    */
  def subscribeToSource(sourceId: String,
                        transforms: Seq[String] = Nil,
                        subscriptionId: String = UUID.randomUUID.toString,
                        retainAfterMatch: Boolean = false): SocketSubscribeRequest = {
    subscribeTo(Map(tags.Id -> sourceId), transforms, subscriptionId, retainAfterMatch)
  }

  /**
    * @param sourceCriteria the criteria for matching data source(s) for the socket represented by this ack to be subscribed to
    * @param transforms any transforms to get the source type identified by 'sourceId' as an 'AddressedMessage'
    * @param subscriptionId a unique id which can be used in order to cancel the subscription
    * @return a request which can connect the sink for this websocket to the given source id via the specified transforms (provided the types match up)
    */
  def subscribeTo(sourceCriteria: Map[String, String],
                  transforms: Seq[String] = Nil,
                  subscriptionId: String = UUID.randomUUID.toString,
                  retainAfterMatch: Boolean = false): SocketSubscribeRequest = {
    SocketSubscribeRequest(commonId, sourceCriteria, subscriptionId, transforms, retainAfterMatch)
  }
}

object SocketConnectionAck {
  implicit val encoder: ObjectEncoder[SocketConnectionAck] = io.circe.generic.semiauto.deriveEncoder[SocketConnectionAck]
  implicit val decoder: Decoder[SocketConnectionAck]       = io.circe.generic.semiauto.deriveDecoder[SocketConnectionAck]
}

/**
  * A message sent over the web socket indicated the sender wants to be sent messages for all sources which match the given
  * criteria.
  *
  * @param socketSinkId the ID of the socket sink we want to connect to the source(s)
  * @param sourceCriteria the data to be used in the 'MetadataCriteria' to match sources
  * @param addressedMessageId a unique ID which can be used to reference/unsubscribe from the source
  * @param transforms any transformations to apply between the sources identified by the
  */
final case class SocketSubscribeRequest(socketSinkId: String,
                                        sourceCriteria: Map[String, String],
                                        addressedMessageId: String,
                                        transforms: Seq[String] = Nil,
                                        retainAfterMatch: Boolean = false) {
  def sourceAsCriteria: MetadataCriteria = MetadataCriteria(sourceCriteria)
  def sinkAsCriteria: MetadataCriteria   = MetadataCriteria.forId(socketSinkId)
  def asAddressedMessage                 = AddressedMessage(this)
}
object SocketSubscribeRequest {
  implicit val encoder: ObjectEncoder[SocketSubscribeRequest] = io.circe.generic.semiauto.deriveEncoder[SocketSubscribeRequest]
  implicit val decoder: Decoder[SocketSubscribeRequest]       = io.circe.generic.semiauto.deriveDecoder[SocketSubscribeRequest]
}

/** @param connectedSourceIds the ids from
  */
final case class SocketSubscribeResponse(matchId: Option[String], connectedSourceIds: Seq[String])
object SocketSubscribeResponse {
  implicit val encoder: ObjectEncoder[SocketSubscribeResponse] = io.circe.generic.semiauto.deriveEncoder[SocketSubscribeResponse]
  implicit val decoder: Decoder[SocketSubscribeResponse]       = io.circe.generic.semiauto.deriveDecoder[SocketSubscribeResponse]

}

final case class SocketUnsubscribeRequest(addressedMessageId: String)
object SocketUnsubscribeRequest {
  implicit val encoder: ObjectEncoder[SocketUnsubscribeRequest] = io.circe.generic.semiauto.deriveEncoder[SocketUnsubscribeRequest]
  implicit val decoder: Decoder[SocketUnsubscribeRequest]       = io.circe.generic.semiauto.deriveDecoder[SocketUnsubscribeRequest]
}
