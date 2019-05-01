package pipelines

import akka.NotUsed
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import monix.execution.Scheduler
import monix.reactive.{Observable, Observer}
import pipelines.core.{Heartbeat, StreamingRequest}

import scala.util.control.NonFatal

package object socket {
  val heartBeatTextMsg: TextMessage.Strict = {
    import io.circe.syntax._
    val heartbeatJson = (Heartbeat: StreamingRequest).asJson.noSpaces
    TextMessage(heartbeatJson)
  }

  def asAddressedMessage(fromClient: Message): AddressedMessage = {
    if (fromClient.isText) {
      val json = fromClient.asTextMessage.asScala.getStrictText
      import io.circe.parser._
      decode[AddressedMessage](json) match {
        case Left(err)      => AddressedMessage.error(s"Couldn't parse message from the client: '$json' : ${err}")
        case Right(message) => message
      }
    } else {
      val d8a = fromClient.asBinaryMessage.asScala.getStrictData.utf8String
      io.circe.parser.decode[AddressedMessage](d8a) match {
        case Left(err)      => AddressedMessage.error(s"Couldn't parse binary message: '$d8a' : ${err}")
        case Right(message) => message
      }
    }
  }

  def asAkkaSink(prefix: String, messages: Observer[AddressedMessage])(implicit scheduler: Scheduler) = {
    val reactiveSub = {
      new socket.WrappedPublisher.WrappedSubscriber[AddressedMessage](prefix, messages.toReactive(scheduler))
    }
    Sink.fromSubscriber(reactiveSub).contramap[Message] { fromRemote: Message =>
      val addressed: AddressedMessage = try {
        asAddressedMessage(fromRemote)
      } catch {
        case NonFatal(e) => AddressedMessage.error(e.getMessage)
      }
      addressed
    }
  }

  def asAkkaSource(prefix: String, messages: Observable[AddressedMessage])(implicit scheduler: Scheduler): Source[Message, NotUsed] = {
    val wsMessages = messages.map { msg =>
      import io.circe.syntax._
      val json = msg.asJson.noSpaces
      if (msg.isBinary) {
        BinaryMessage(ByteString(json.getBytes("UTF-8")))
      } else {
        TextMessage(json)
      }
    }

    val reactive = {
      new WrappedPublisher(prefix, wsMessages.toReactivePublisher(scheduler))
    }
    Source.fromPublisher(reactive)

  }

}
