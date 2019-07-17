package pipelines.rest.socket

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import io.circe.{Decoder, Encoder, ObjectEncoder}

import scala.reflect.ClassTag
import scala.util.{Failure, Try}

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

  def as[T: ClassTag: Decoder]: Try[T]
}
object AddressedMessage {
  import cats.syntax.functor._
  import io.circe.syntax._

  /**
    * So, the AddressedMessage 'to' field can be used to filter on by different listeners over a single socket.
    *
    * Although it could be anything, here are some well-know
    */
  object topics {
    val ErrorTopic       = "__ERRORS__"
    val HeartbeatTopic   = "__HEARTBEAT__"
    val SubscribeTopic   = "__SUBSCRIBE__"
    val UnsubscribeTopic = "__UNSUBSCRIBE__"

    def forClass(name: String): String         = s"class:${name}"
    def forClass[T: ClassTag]: String          = forClass(className[T])
    private def className[T: ClassTag]: String = implicitly[ClassTag[T]].runtimeClass.getName
  }

  def error(text: String) = apply(topics.ErrorTopic, text)

  def heartbeat(now: ZonedDateTime = ZonedDateTime.now()): AddressedTextMessage = {
    apply(topics.HeartbeatTopic, DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now))
  }

  def any(text: String): AddressedTextMessage = apply("", text)

  def apply[T: ClassTag: Encoder](value: T): AddressedTextMessage = {
    apply(topics.forClass[T], value.asJson.noSpaces)
  }

  def apply[T: ClassTag: Encoder](to: String, value: T): AddressedTextMessage = {
    apply(to, value.asJson.noSpaces)
  }

  def apply(to: String, text: String) = new AddressedTextMessage(to, text)

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

  def as[T: ClassTag: Decoder]: Try[T] = {
    val expectedTopic = AddressedMessage.topics.forClass[T]
    if (expectedTopic == to) {
      io.circe.parser.decode[T](text).toTry
    } else {
      Failure(new Exception(s"Topic '$to' doesn't match '$expectedTopic'"))
    }
  }
}
object AddressedTextMessage {
  implicit val encoder: ObjectEncoder[AddressedTextMessage] = io.circe.generic.semiauto.deriveEncoder[AddressedTextMessage]
  implicit val decoder: Decoder[AddressedTextMessage]       = io.circe.generic.semiauto.deriveDecoder[AddressedTextMessage]
}
case class AddressedBinaryMessage(override val to: String, data: Array[Byte]) extends AddressedMessage {
  override def isBinary = true

  // TODO - this is naive and untested - we probably need to refact this as the bytes could be protobuf, avro, etc
  def as[T: ClassTag: Decoder]: Try[T] = {
    val text = new String(data, "UTF-8")
    io.circe.parser.decode[T](text).toTry
  }
}
object AddressedBinaryMessage {
  implicit val encoder: ObjectEncoder[AddressedBinaryMessage] = io.circe.generic.semiauto.deriveEncoder[AddressedBinaryMessage]
  implicit val decoder: Decoder[AddressedBinaryMessage]       = io.circe.generic.semiauto.deriveDecoder[AddressedBinaryMessage]
}
