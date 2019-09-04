package pipelines.rest.socket

import io.circe.Json
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import org.scalatest.concurrent.ScalaFutures
import pipelines.BaseCoreTest
import pipelines.reactive._
import pipelines.rest.socket.handlers.SubscriptionHandler
import pipelines.users.Claims

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.util.Success

class ServerSocketTest extends BaseCoreTest with ScalaFutures {

  override def testTimeout: FiniteDuration = 1.second

  "ServerSocket.toClient data sink" should {
    "be able to subscribe to multiple data sources (we'll want to send data through the socket from multiple places concurrently)" in {
      withScheduler { implicit sched: Scheduler =>
        val socket   = ServerSocket(sched)
        val received = ListBuffer[AddressedMessage]()
        socket.toClientAkkaInput.foreach { msg =>
          received += msg
        }
        // send messages from different observables (e.g. each will complete)
        Observable(AddressedMessage("First")).subscribe(socket.toClient)
        Observable(AddressedMessage("Second")).subscribe(socket.toClient)

        eventually {
          received should contain only (AddressedMessage("First"), AddressedMessage("Second"))
        }
      }
    }
  }
  "ServerSocket.register" should {
    "send messages through the router" in {
      withScheduler { implicit sched: Scheduler =>
        Given("A ServerSocket and command router")
        val received = ListBuffer[AddressedMessage]()
        val router = AddressedMessageRouter().addGeneralHandler {
          case (_, msg) => received += msg
        }
        val socket = ServerSocket(sched)

        When("We 'register' the socket with a pipelines service and the command router")
        socket.register(Claims.forUser("bob"), Map("test" -> "handshake"), PipelineService(), router).futureValue

        Then("Data sent 'from the client' should be routed/handled by the command router")
        socket.dataFromClientInput.onNext(AddressedMessage("Some Data")).futureValue
        eventually {
          received.size shouldBe 1
          received.head shouldBe AddressedMessage("Some Data")
        }
      }
    }
    "handle SocketConnectionAckRequest messages once registered" ignore {
      withScheduler { implicit sched: Scheduler =>
        val service: PipelineService = PipelineService()(sched)
        val router                   = AddressedMessageRouter()

        router.addGeneralHandler {
          case (user, msg) =>
            println(s"""
                       |
                       |  @@@@@@@ GENERAL  user $user got $msg
                       |
                       |""".stripMargin)
        }

        val socket                                   = ServerSocket(sched)
        val user                                     = Claims.forUser("bob")
        val (socketSource, _, socketSink) = socket.register(user, Map("test" -> "handshake"), service, router).futureValue

        val received = ListBuffer[AddressedMessage]()
        socketSink.socket.toClientAkkaInput.foreach { msg =>
          println(s"""
                     |
                     |  @@@@@@@ HANDSHAKE $user got $msg
                     |
                     |""".stripMargin)
          received += msg
        }
        val fut: CancelableFuture[List[AddressedMessage]] = socketSink.socket.toClientAkkaInput.take(1).toListL.runToFuture
        socketSource.socket.dataFromClientInput.onNext(AddressedMessage(SocketConnectionAckRequest())).futureValue

        fut.futureValue.size shouldBe 1

        eventually {
          received.size shouldBe 1
        }
      }
    }
  }

  "ServerSocket" should {

    "Add socket listeners which will apply subscribe/unsubscribe requests from SocketSources in order to connect 'em to available sources/sinks" ignore {
      withScheduler { implicit sched: Scheduler =>
        Given("We've set up a SubscriptionHandler to handle subscribe/unsubscribe requests which are sent over the socket")
        val service: PipelineService = PipelineService()(sched)
        // create a new router...
        val router = AddressedMessageRouter()
        // ... and have the SubscriptionHandler register interest in subscribe/unsubscribe messages
        SubscriptionHandler.register(router, service)

        val receivedSocketSubscribeRequests = ListBuffer[SocketSubscribeRequest]()
        router.addHandler[SocketSubscribeRequest] {
          case (user, msg) =>
            receivedSocketSubscribeRequests ++= msg.as[SocketSubscribeRequest].toOption
            println(s"""
                       |
                       |  @@@@@@@  user $user got $msg
                       |
                       |""".stripMargin)
        }

        router.addGeneralHandler {
          case (user, msg) =>
            println(s"""
                       |
                       |  @@@@@@@ GENERAL  user $user got $msg
                       |
                       |""".stripMargin)
        }

        And("Create/register a new socket source with that pipeline")
        val socket                                = ServerSocket(sched)
        val user                                  = Claims.forUser("bob")
        val (socketSource, handshake, socketSink) = socket.register(user, Map("test" -> "handshake"), service, router).futureValue

        When("A 'subscribe' message is sent which connects the 'pipeline source events' source with this 'socket sink' sink")
        locally {
          service.pipelines.size shouldBe 0

          val r = SocketSubscribeRequest(sinkId = socketSink.id.get,
                                         sourceCriteria = Map(pipelines.reactive.tags.Id -> socketSource.id.get),
                                         "doesn't matter uuid",
                                         retainAfterMatch = true)

          socket.dataFromClientInput.onNext(AddressedMessage(r)).futureValue

          eventually {
            service.pipelines.size shouldBe 1
          }
        }

        And("A new SocketSource and sink are created")
        And("Some push source to which we can subscribe")
        val (true, pushSource) = service.pushSourceForName[PushEvent]("pushMePullYou", true, false, Map("foo" -> "bar", "user" -> "one")).futureValue

        And("The socket sends a subscription request for a source")
        val createPipelineFuture = service.pipelineCreatedEvents.dump("pipelineCreatedEvents").take(1).toListL.runToFuture
        val subscribeRequest: SocketSubscribeRequest =
          handshake.subscribeTo(Map("foo" -> "bar"), transforms = Seq(Transform.keys.PushEventAsAddressedMessage), retainAfterMatch = true)
        socketSource.socket.dataFromClientInput.onNext(subscribeRequest.asAddressedMessage)
        Then("The command handler for the socket should receive the incoming message")
        eventually {
          receivedSocketSubscribeRequests should contain only (subscribeRequest)
        }

        Then("A new pipeline should be created between the push source and our socket sink")
        val list           = createPipelineFuture.futureValue
        val List(pipeline) = list
        pipeline.matchId should not be null

        When("The source pushes some data")
        val readPushedDataFuture  = socketSource.socket.toClientAkkaInput.take(1).toListL.runToFuture
        val readPushed2DataFuture = socketSource.socket.toClientAkkaInput.take(2).toListL.runToFuture
        val firstSourceMessage    = PushEvent(Claims.after(10.seconds).forUser("alice"), Json.fromString("alice data"))
        pushSource.push(firstSourceMessage)

        Then("Data from that source should be connected w/ the socket sink")
        val List(readBack) = readPushedDataFuture.futureValue

        When("A second source is added which also meets the subscription criteria")
        val createPipeline2Future = service.pipelineCreatedEvents.dump("pipelineCreatedEvents (2)").take(1).toListL.runToFuture
        val newPush               = DataSource.push[PushEvent](Map("foo" -> "bar", "user" -> "two")).ensuringId(Ids.next())
        val (pushSource2, _)      = service.sources.add(newPush)
        val List(_)               = createPipeline2Future.futureValue
        val secondSourceMessage   = PushEvent(Claims.after(10.seconds).forUser("second"), Json.fromString("second"))
        pushSource2.push(secondSourceMessage)

        Then("data from both sources should come through the fucking socket")
        val List(firstMsg, secondMsg) = readPushed2DataFuture.futureValue
        firstMsg.as[PushEvent] shouldBe Success(firstSourceMessage)
        secondMsg.as[PushEvent] shouldBe Success(secondSourceMessage)
      }
    }
  }

  "ServerSocket.toClientInput" ignore {
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
      val done: CancelableFuture[Unit] = socket.toClientAkkaInput.foreach {
        case msg: AddressedTextMessage =>
          val before = received.size
          received += msg
          received.size shouldBe before + 1
        case other => sys.error(s"got $other")
      }

      (first ++ Observable.never).delayOnNext(1.millis).subscribe(socket.toClient)
      second.delayOnNext(1.millis).subscribe(socket.toClient)

      Then("It should contain the values from both")
      done.futureValue

      eventually {
        received.size shouldBe firstValues.size + secondValues.size
      }
      received.sortBy(_.text) should contain theSameElementsAs (firstValues ++ secondValues).sortBy(_.text)
    }
  }
}
