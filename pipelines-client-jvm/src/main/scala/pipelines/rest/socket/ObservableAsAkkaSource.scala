package pipelines.rest.socket

import akka.NotUsed
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import monix.execution.Scheduler
import monix.reactive.Observable

object ObservableAsAkkaSource {

  def apply(prefix: String, messages: Observable[AddressedMessage], scheduler: Scheduler, leaveOpen: Boolean): Source[Message, NotUsed] = {
    val wsMessages = messages.map { msg: AddressedMessage =>
      import io.circe.syntax._
      val json = msg.asJson.noSpaces
      if (msg.isBinary) {
        BinaryMessage(ByteString(json.getBytes("UTF-8")))
      } else {
        TextMessage(json)
      }
    }

    val reactive = new WrappedPublisher(prefix, wsMessages.toReactivePublisher(scheduler), leaveOpen)
    Source.fromPublisher(reactive)

  }
}
