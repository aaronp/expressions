package pipelines.rest

import akka.NotUsed
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import pipelines.core.{Heartbeat, StreamingRequest}

import scala.util.control.NonFatal

package object socket extends StrictLogging {
  val heartBeatTextMsg: TextMessage.Strict = {
    import io.circe.syntax._
    val heartbeatJson = (Heartbeat: StreamingRequest).asJson.noSpaces
    TextMessage(heartbeatJson)
  }

  def asAddressedMessage(fromClient: Message): AddressedMessage = {
    if (fromClient.isText) {
      val json = fromClient.asTextMessage.asScala.getStrictText
      logger.info(s"got txt from client: $json")
      import io.circe.parser._
      decode[AddressedMessage](json) match {
        case Left(err)      => AddressedMessage.error(s"Couldn't parse message from the client: '$json' : ${err}")
        case Right(message) => message
      }
    } else {
      logger.info(s"got binary from client: $fromClient")
      val d8a = fromClient.asBinaryMessage.asScala.getStrictData.utf8String
      io.circe.parser.decode[AddressedMessage](d8a) match {
        case Left(err)      => AddressedMessage.error(s"Couldn't parse binary message: '$d8a' : ${err}")
        case Right(message) => message
      }
    }
  }

}
