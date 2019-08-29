package pipelines.rest.socket

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.ExpectedWebSocketRequestRejection
import monix.reactive.Observable
import pipelines.reactive.PipelineService
import pipelines.rest.DevConfig
import pipelines.rest.routes.{BaseRoutesTest, WebSocketTokenCache}
import pipelines.rest.socket.handlers.SubscriptionHandler
import pipelines.users.Claims
import pipelines.users.jwt.RichClaims._
import pipelines.{Env, Using}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class SocketRoutesTest extends BaseRoutesTest {
  val settings = DevConfig.secureSettings
  val user     = Claims.after(5.minutes).forUser("server")
  val jwt      = user.asToken(settings.secret)

  "SocketRoutes.connectAnySocket" should {

    "echo client messages back" in Using(Env()) { env =>
      val client = ClientSocket(SocketSettings("client"))(env.ioScheduler)

      val service       = PipelineService()(env.ioScheduler)
      val commandRouter = new AddressedMessageRouter()
      val subsc         = SubscriptionHandler.register(commandRouter, service)

      val underTest = new SocketRoutes(settings, WebSocketTokenCache(1.minute)(env.ioScheduler), subsc, service) {
        override def handleSocket(user: Claims, socket: ServerSocket, queryMetadata: Map[String, String]) = {
          implicit val s = env.ioScheduler
          socket.fromRemoteOutput.subscribe(socket.toClient)
        }
      }

      val clientMessages = ListBuffer[AddressedMessage]()
      client.fromServer.foreach {
        case am @ AddressedTextMessage(_, _) =>
          clientMessages += am
        case other => sys.error("wrong" + other)
      }(env.ioScheduler)

      WS("/sockets/connect", client.akkaFlow) ~> underTest.connectAnySocket(user) ~> check {
        isWebSocketUpgrade shouldEqual true

        Observable
          .interval(100.millis)
          .foreach { _ =>
            client.toServerInput.onNext(AddressedMessage("hello", "there"))
          }(env.ioScheduler)

        eventually {
          clientMessages.size should be > 1
        }
      }
    }
  }
  "SocketRoutes" should {
    val socketConnectionRequest = Get("/sockets/connect").withHeaders(Authorization(OAuth2BearerToken(jwt)))

    "upgrade a GET request to a WS request for new users" in Using(Env()) { env =>
      val socketList = ListBuffer[(Claims, ServerSocket)]()

      val service = PipelineService()(env.ioScheduler)
      val commandRouter = new AddressedMessageRouter()
      val subsc         = SubscriptionHandler.register(commandRouter, service)

      val underTest = new SocketRoutes(settings, WebSocketTokenCache(1.minute)(env.ioScheduler), subsc, service) {
        override def handleSocket(user: Claims, socket: ServerSocket, queryMetadata: Map[String, String]) = {
          socketList += (user -> socket)
          user.name
        }
      }

      socketConnectionRequest ~> underTest.routes ~> check {
        val List((claims, _)) = socketList.toList
        claims shouldBe user
        rejections should contain(ExpectedWebSocketRequestRejection)
      }
    }
  }
}
