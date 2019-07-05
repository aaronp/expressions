package pipelines.rest.socket

import akka.http.scaladsl.server.Directives.{extractExecutionContext, get, path, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.WSProbe
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import pipelines.rest.DevConfig
import pipelines.rest.routes.{BaseRoutesTest, StaticFileRoutes}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class BaseSocketRoutesTest extends BaseRoutesTest {
  "SocketRoutes" should {
    "reuse an existing socket between routes" in {

      Given("A SocketRoutes which creates a new socket when given some key")
      val underTest = new BaseSocketRoutesTest.UnderTest
      val wsClient  = WSProbe()

      And("The ability to connect a second socket")
      def createSecondFeed() = {
        WS("/socket/someId/strings", wsClient.flow) ~> underTest.route ~> check {
          isWebSocketUpgrade shouldEqual false
          responseText shouldBe "someId"
        }
      }

      def pushMessage() = {
        Get("/single/someId") ~> underTest.route ~> check {
          status.intValue shouldBe 200
        }
      }

      When("An initial socket connection is made")
      WS("/socket/someId/numbers", wsClient.flow) ~> underTest.route ~> check {

        isWebSocketUpgrade shouldEqual true

        And("Another socket connection to a different URL")
        createSecondFeed()

        def nextMsg(): AddressedMessage = {
          val expectedMsg = wsClient.expectMessage()
          val json        = expectedMsg.asTextMessage.getStrictText
          io.circe.parser.decode[AddressedMessage](json).toTry.get
        }

        Then("The client should contain messages from both feeds")
        val firstMsg = nextMsg
        val messages = Iterator.continually(nextMsg).take(100000)
        messages.find(_.to != firstMsg.to).get.to should not be (firstMsg.to)

        When("We use normal (non-socket) route which can send data")
        pushMessage()

        Then("We should see the single message in our consumer")
        messages.find(_ == underTest.singleMessage) should not be (empty)

        wsClient.sendCompletion()
      }
    }
  }

}

object BaseSocketRoutesTest {

  class UnderTest(implicit sched: ExecutionContext) extends BaseSocketRoutes(DevConfig.secureSettings) {

    val received      = ListBuffer[AddressedMessage]()
    val singleMessage = AddressedMessage("single", "data")

    def pushNumbers: Route = {
      path("socket" / Segment / "numbers") { id =>
        withSocketRoute(defaultSettings(id)) {
          case (sched, socket) =>
            val data: Observable[AddressedTextMessage] = Observable.interval(100.millis).zipWithIndex.map(_._2.toInt).map { i =>
              AddressedMessage("numbers", i.toString)
            }
            val cancelable: Cancelable = (data ++ Observable.never).subscribe(socket.toServerFromRemote)(sched)
            socket.addClientSubscription(cancelable).toString
        }
      }
    }
    def pushStrings: Route = {
      path("socket" / Segment / "strings") { id =>
        withSocketRoute(defaultSettings(id)) {
          case (sched, socket) =>
            val data: Observable[AddressedTextMessage] = Observable.interval(100.millis).zipWithIndex.map(_._2.toInt).map { i =>
              AddressedMessage("strings", "x" * ((i % 10) + 1))
            }
            val cancelable: Cancelable = (data ++ Observable.never).subscribe(socket.toServerFromRemote)(sched)
            socket.addClientSubscription(cancelable).toString

        }
      }
    }
    def cancel: Route = {
      path("socket" / Segment / "cancel" / JavaUUID) { (id, taskId) =>
        withSocketRoute(defaultSettings(id)) {
          case (_, socket) => socket.cancelSubscription(taskId).toString
        }
      }
    }
    def consumeData: Route = {
      path("socket" / Segment / "push") { id =>
        withSocketRoute(defaultSettings(id)) {
          case (sched, socket) =>
            val cancelable = socket.toRemoteAkkaInput.foreach { msg: AddressedMessage =>
              received += msg
            }(sched)
            socket.addClientSubscription(cancelable).toString
        }
      }
    }
    def pushSingle: Route = {
      path("single" / Segment) { id =>
        extractExecutionContext { ec =>
          implicit val sched = Scheduler.apply(ec)

          val socket = ServerSocket(defaultSettings(id))

          socket.toServerFromRemote.onNext(singleMessage)
          complete("ok")
        }
      }
    }

    def route: Route = {
      get {
        pushNumbers ~
          pushStrings ~
          consumeData ~
          pushSingle ~
          cancel
      }
    }
  }
}
