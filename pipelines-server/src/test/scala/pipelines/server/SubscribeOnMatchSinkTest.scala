package pipelines.server

import io.circe.Json
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.{Consumer, Observable}
import pipelines.Pipeline
import pipelines.reactive.{tags => rTags, _}
import pipelines.rest.socket._
import pipelines.users.Claims

import scala.concurrent.duration._
import scala.util.Success

class SubscribeOnMatchSinkTest extends BaseServiceSpec {

  override def testTimeout: FiniteDuration = 10.seconds

  "SubscribeOnMatchSink.addressedMessageRoutingSink" should {
    "subscribe sources to sinks via transformations to AddressedMessages" in {
      withScheduler { implicit s =>
        val service = PipelineService()

        Given("A new socket sink as if a new user connected")
        val socket: ServerSocket = ServerSocket(s)
        val socketSink: SocketSink = {
          val userForWhomThisSocketWasCreated = Claims.after(1.minute).forUser("anybody")

          val Seq(newSink) = service.getOrCreateSink(SocketSink(userForWhomThisSocketWasCreated, socket, Map("test" -> "yup")))
          newSink.asInstanceOf[SocketSink]
        }

        val createdPipelineFuture: CancelableFuture[List[Pipeline[_, _]]] = service.pipelineCreatedEvents.take(1).toListL.runToFuture

        And("a push source as if from a REST endpoint")
        val (true, pushSource) = service.pushSourceForName[PushEvent]("pushIt", true, false, Map.empty).futureValue

        When("We use the SubscribeOnMatchSink.addressedMessageRoutingSink to send a subscribe message interpret an 'AddressedMessage' to ")
        val underTest                                  = new SubscribeOnMatchSink(service)
        val consumer: Consumer[AddressedMessage, Unit] = underTest.addressedMessageRoutingSink.consumer

        val subscriptionMessage: AddressedMessage = {
          val sourceCriteria = Map(rTags.Id -> pushSource.id.get)
          val transforms     = Seq(Transform.keys.Dump, Transform.keys.PushEventAsAddressedMessage)
          AddressedMessage(SocketSubscribeRequest(socketSink.id.get, sourceCriteria, "referenceId", transforms))
        }

        Observable(subscriptionMessage).consumeWith(consumer).runToFuture.futureValue

        Then("A pipeline match should be triggered")
        val List(created: Pipeline[_, _]) = createdPipelineFuture.futureValue
        //created.resultFuture.futureValue

        Then("The sink should observe messages sent from the source and send 'em to the client")
        // we use the 'toRemoteAkkaInput' here as that's the place from which Akka Streams will take the data and
        // flush it to the websocket channel -- this test isn't a full integration test where we actually spin up
        // a web socket and connect an end client, we just observe what would be sent to said client, assuming AkkaIO
        // works
        val receivedOnSocketFuture = socket.toRemoteAkkaInput.dump("toRemoteOutput").take(1).toListL.runToFuture
        val fromBob                = PushEvent(Claims.after(10.seconds).forUser("bob"), Json.fromString("hello from bob"))
        pushSource.push(fromBob)

        val List(gotIt) = receivedOnSocketFuture.futureValue
        gotIt.as[PushEvent] shouldBe Success(fromBob)
      }
    }
//    "receive unsubscribe messages from new SocketSources" in {
//      ???
//    }
  }
  "SubscribeOnMatchSink.isSocketMessage" should {
    "return true for AddressedMessages containing only SocketSubscribeRequest or SocketUnsubscribeRequest messages" in {
      withScheduler { implicit sched: Scheduler =>
        val subscribeInput = SocketSubscribeRequest("socketId", Map("x" -> "y"), "bar")

        val dataSource = DataSource[AddressedMessage](
          Observable(
            AddressedMessage(SocketUnsubscribeRequest("socketSinkId")),
            AddressedMessage("Something Else"),
            AddressedMessage(subscribeInput),
            AddressedMessage("topic", "123")
          ))
        val Some(filtered) = SubscribeOnMatchSink.isSocketMessage.applyTo(dataSource)

        val List(unsubscribe, subscribe) = filtered.asObservable[AddressedMessage].toListL.runSyncUnsafe(testTimeout)
        unsubscribe.as[SocketUnsubscribeRequest] shouldBe Success(SocketUnsubscribeRequest("socketSinkId"))
        subscribe.as[SocketSubscribeRequest] shouldBe Success(subscribeInput)
      }
    }
  }
  "PipelineListeners.listenToNewSocketSources" should {
    "Add socket listeners which will apply subscribe/unsubscribe requests from SocketSources in order to connect 'em to available sources/sinks" in {
      withScheduler { implicit sched: Scheduler =>
        Given("We've used listenToNewSocketSources to ensure 'subscribe' requests from new sockets are handled")
        val service = PipelineService()(sched)
        SubscribeOnMatchSink.listenToNewSocketSources(service)

        When("A new SocketSource and sink are created")
        val socket                       = ServerSocket(sched)
        val user                         = Claims.after(1.minute).forUser("bob")
        val (socketSource, handshake, _) = socket.register(user, Map.empty, service).futureValue

        And("Some push source to which we can subscribe")
        val (true, pushSource) = service.pushSourceForName[PushEvent]("pushMePullYou", true, false, Map("foo" -> "bar", "user" -> "one")).futureValue

        And("The socket sends a subscription request for a source")
        val createPipelineFuture = service.pipelineCreatedEvents.dump("pipelineCreatedEvents").take(1).toListL.runToFuture
        socketSource.socket.toServerFromRemote
          .onNext(handshake.subscribeTo(Map("foo" -> "bar"), transforms = Seq(Transform.keys.PushEventAsAddressedMessage), retainAfterMatch = true).asAddressedMessage)

        Then("A new pipeline should be created between the push source and our socket sink")
        val list           = createPipelineFuture.futureValue
        val List(pipeline) = list
        pipeline.matchId should not be null

        When("The source pushes some data")
        val readPushedDataFuture  = socketSource.socket.toRemoteAkkaInput.take(1).toListL.runToFuture
        val readPushed2DataFuture = socketSource.socket.toRemoteAkkaInput.take(2).toListL.runToFuture
        val firstSourceMessage    = PushEvent(Claims.after(10.seconds).forUser("alice"), Json.fromString("alice data"))
        pushSource.push(firstSourceMessage)

        Then("Data from that source should be connected w/ the socket sink")
        val List(readBack) = readPushedDataFuture.futureValue

        When("A second source is added which also meets the subscription criteria")
        val createPipeline2Future = service.pipelineCreatedEvents.dump("pipelineCreatedEvents (2)").take(1).toListL.runToFuture
        val newPush               = DataSource.push[PushEvent](Map("foo" -> "bar", "user" -> "two")).ensuringId()

        val (pushSource2, _)    = service.sources.add(newPush)
        val List(_)             = createPipeline2Future.futureValue
        val secondSourceMessage = PushEvent(Claims.after(10.seconds).forUser("second"), Json.fromString("second"))
        pushSource2.push(secondSourceMessage)

        Then("data from both sources should come through the fucking socket")
        val List(firstMsg, secondMsg) = readPushed2DataFuture.futureValue
        firstMsg.as[PushEvent] shouldBe Success(firstSourceMessage)
        secondMsg.as[PushEvent] shouldBe Success(secondSourceMessage)
      }
    }
  }
}
