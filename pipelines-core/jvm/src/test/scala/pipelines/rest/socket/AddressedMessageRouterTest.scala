package pipelines.rest.socket

import io.circe.{Decoder, ObjectEncoder}
import pipelines.BaseCoreTest
import pipelines.users.Claims

class AddressedMessageRouterTest extends BaseCoreTest {
  import AddressedMessageRouterTest._

  "AddressedMessageRouter.addHandler" should {
    "route messages send via 'onAddressedMessage' to the given handler for the same type" in {
      val router = new AddressedMessageRouter()

      var receivedMessages: List[AddressedMessage] = List[AddressedMessage]()

      router.addHandler[TestMsg] { (_, next: AddressedMessage) =>
        receivedMessages = next :: receivedMessages
      }

      val user = Claims.forUser("dave")
      router.onAddressedMessage(user, AddressedMessage(TestMsg("Hello")))
      router.onAddressedMessage(user, AddressedMessage("world"))
      receivedMessages should contain(AddressedMessage(TestMsg("Hello")))
      receivedMessages should not contain (AddressedMessage("world"))
    }
  }
}

object AddressedMessageRouterTest {

  case class TestMsg(name: String)
  object TestMsg {
    implicit val encoder: ObjectEncoder[TestMsg] = io.circe.generic.semiauto.deriveEncoder[TestMsg]
    implicit val decoder: Decoder[TestMsg]       = io.circe.generic.semiauto.deriveDecoder[TestMsg]
  }

}
