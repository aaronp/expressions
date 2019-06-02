package pipelines.socket

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder, ObjectEncoder}

/**
  * This was created to wrap websocket messages so that a single websocket connection can be used by multiple producers/consumers.
  *
  * Instead of having an otherwise dedicated websocket connection for all its messages, Anything which wants to send binary or text
  * websocket messages can wrap them with one of these puppies and make use of an already opened connection, just with the
  * additional overhead of checking the 'to' field so that the shared connection can route the requests appropriately.
  *
  * See SocketRoutes for more details
  */
sealed trait AddressedMessage {
  def isBinary: Boolean
  def to: String
}
object AddressedMessage {
  import cats.syntax.functor._
  import io.circe.syntax._

  val ErrorTopic = "__ERRORS__"
  val HeartbeatTopic = "__HEARTBEAT__"

  def error(text: String) = apply(ErrorTopic, text)

  def heartbeat(now : ZonedDateTime = ZonedDateTime.now()): AddressedTextMessage = {
    apply(HeartbeatTopic, DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now))
  }

  def any(text: String): AddressedTextMessage = apply("", text)

  def apply(to: String, text: String)      = new AddressedTextMessage(to, text)
  def apply(to: String, data: Array[Byte]) = new AddressedBinaryMessage(to, data)

  implicit val encoder: Encoder[AddressedMessage] = Encoder.instance {
    case msg @ AddressedTextMessage(_, _)   => msg.asJson
    case msg @ AddressedBinaryMessage(_, _) => msg.asJson
  }

  implicit val decoder: Decoder[AddressedMessage] =
    List[Decoder[AddressedMessage]](
      Decoder[AddressedBinaryMessage].widen,
      Decoder[AddressedTextMessage].widen
    ).reduceLeft(_ or _)
}
case class AddressedTextMessage(override val to: String, text: String) extends AddressedMessage {
  override def isBinary = false
}
object AddressedTextMessage {
  implicit val encoder: ObjectEncoder[AddressedTextMessage] = io.circe.generic.semiauto.deriveEncoder[AddressedTextMessage]
  implicit val decoder: Decoder[AddressedTextMessage]       = io.circe.generic.semiauto.deriveDecoder[AddressedTextMessage]
}
case class AddressedBinaryMessage(override val to: String, data: Array[Byte]) extends AddressedMessage {
  override def isBinary = true
}
object AddressedBinaryMessage {
  implicit val encoder: ObjectEncoder[AddressedBinaryMessage] = io.circe.generic.semiauto.deriveEncoder[AddressedBinaryMessage]
  implicit val decoder: Decoder[AddressedBinaryMessage]       = io.circe.generic.semiauto.deriveDecoder[AddressedBinaryMessage]
}
