package pipelines.socket

import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.server.ExpectedWebSocketRequestRejection
import monix.execution.Scheduler
import monix.reactive.Observable
import pipelines.rest.DevConfig
import pipelines.rest.jwt.Claims
import pipelines.rest.routes.BaseRoutesTest
import pipelines.{Env, Using, socket}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class SocketRoutesTest extends BaseRoutesTest {
  val settings = DevConfig.secureSettings
  val user     = Claims.after(5.minutes).forUser("server")
  val jwt      = user.asToken(settings.secret)

  "SocketRoutes.connectAnySocket" should {

    "work" in Using(Env()) { env =>
      val client = ClientSocket(SocketSettings("client"))(env.ioScheduler)

      val handler   = SocketRoutesSettings(DevConfig(), DevConfig.secureSettings, env).socketHandler
      val underTest = new SocketRoutes(settings, handler)

      val clientMessages = ListBuffer[AddressedMessage]()
      client.toClientOutput.foreach {
        case am @ AddressedTextMessage(_, msg) if msg.startsWith("echo:") =>
          clientMessages += am
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

    "upgrade a GET request to a WS request for new users" in {
      val sockets = ListBuffer[(Claims, ServerSocket)]()
      def handler(user: Claims, socket: ServerSocket): String = {
        sockets += (user -> socket)
        user.name
      }

      val underTest = new SocketRoutes(settings, handler)

      socketConnectionRequest ~> underTest.routes ~> check {
        val List((claims, _)) = sockets.toList
        claims shouldBe user
        rejection shouldBe ExpectedWebSocketRequestRejection
      }
    }
    "reuse the same route for a user" in {
      val sockets = ListBuffer[(Claims, ServerSocket)]()
      def handler(user: Claims, socket: ServerSocket): String = {
        sockets += (user -> socket)
        user.name
      }
      val cache     = new socket.BaseSocketRoutes.SocketCache
      val underTest = new SocketRoutes(settings, handler, cache)

      // create a socket which will be reused/cached
      val created = cache.socketFor(SocketSettings(user.name))(Scheduler(system.dispatcher))
      created.isLeft shouldBe true

      socketConnectionRequest ~> underTest.routes ~> check {
        val List((claims, _)) = sockets.toList
        claims shouldBe user
        response.status.intValue shouldBe 200
      }
    }
  }

  override def testTimeout: FiniteDuration = 12.seconds
}
