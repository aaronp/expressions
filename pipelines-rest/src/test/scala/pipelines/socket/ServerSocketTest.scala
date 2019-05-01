package pipelines.socket

import monix.execution.CancelableFuture
import monix.reactive.Observable
import org.scalatest.concurrent.ScalaFutures
import pipelines.BaseCoreTest

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class ServerSocketTest extends BaseCoreTest with ScalaFutures {

  "ServerSocket.toClientInput" should {
    "be able to be subscribed to multiple observables" in withScheduler { implicit sched =>
      Given("A ServerSocket")
      val socket = ServerSocket(sched)

      And("2 different observables")
      val firstValues = (0 to 10).map { i =>
        AddressedMessage("first", i.toString)
      }
      val first: Observable[AddressedMessage] = Observable.fromIterable(firstValues)

      val secondValues = (100 to 110).map { i =>
        AddressedMessage("second", i.toString)
      }
      val second = Observable.fromIterable(secondValues)

      val received = ListBuffer[AddressedTextMessage]()
      When("We subscribe it to both observables")
      val done: CancelableFuture[Unit] = socket.output.foreach {
        case msg: AddressedTextMessage =>
          val before = received.size
          received += msg
          received.size shouldBe before + 1
      }

      (first ++ Observable.never).delayOnNext(1.millis).subscribe(socket.toRemote)
      second.delayOnNext(1.millis).subscribe(socket.toRemote)

      Then("It should contain the values from both")
      done.futureValue

      eventually {
        received.size shouldBe firstValues.size + secondValues.size
      }
      received.sortBy(_.text) should contain theSameElementsAs (firstValues ++ secondValues).sortBy(_.text)
    }
  }
}
