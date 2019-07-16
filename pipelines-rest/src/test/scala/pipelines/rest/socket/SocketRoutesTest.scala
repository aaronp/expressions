package pipelines.rest.socket

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.ExpectedWebSocketRequestRejection
import monix.reactive.Observable
import pipelines.rest.DevConfig
import pipelines.rest.routes.{BaseRoutesTest, WebSocketTokenCache}
import pipelines.users.Claims
import pipelines.{Env, Using}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import pipelines.users.jwt.RichClaims._

class SocketRoutesTest extends BaseRoutesTest {
  val settings = DevConfig.secureSettings
  val user     = Claims.after(5.minutes).forUser("server")
  val jwt      = user.asToken(settings.secret)

  "SocketRoutes.connectAnySocket" should {

    "echo client messages back" in Using(Env()) { env =>
      val client = ClientSocket(SocketSettings("client"))(env.ioScheduler)

      def handler(user: Claims, socket: ServerSocket, queryParams: Map[String, String]) = {
        implicit val s = env.ioScheduler
        socket.fromRemoteOutput.subscribe(socket.toServerFromRemote)
      }

      val underTest = new SocketRoutes(settings, WebSocketTokenCache(1.minute)(env.ioScheduler), handler)

      val clientMessages = ListBuffer[AddressedMessage]()
      client.toClientOutput.foreach {
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
      val sockets = ListBuffer[(Claims, ServerSocket)]()
      def handler(user: Claims, socket: ServerSocket, queryParams: Map[String, String]): String = {
        sockets += (user -> socket)
        user.name
      }

      val underTest = new SocketRoutes(settings, WebSocketTokenCache(1.minute)(env.ioScheduler), handler)

      socketConnectionRequest ~> underTest.routes ~> check {
        val List((claims, _)) = sockets.toList
        claims shouldBe user
        rejections should contain(ExpectedWebSocketRequestRejection)
      }
    }
  }

  override def testTimeout: FiniteDuration = 12.seconds
}
