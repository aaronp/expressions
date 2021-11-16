package expressions.rest.server.kafka

import cats.effect.kernel.Async
import cats.effect.std.Queue
import com.typesafe.config.{Config, ConfigFactory}
import expressions.rest.server.record.LiveRecorder
import expressions.rest.server.{RestRoutes, StaticFileRoutes}
import fs2.Pipe
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, Logger}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.websocket.WebSocketFrame.Text
import zio.{ExitCode, Task, ZIO}
import zio.interop.catz.*
import zio.interop.catz.implicits.*
//import zio.duration.{given, *}
import concurrent.duration.{given, *}
import scala.annotation.implicitNotFound
import scala.concurrent.ExecutionContext

/**
  * I just ripped this off from
  * https://github.com/http4s/http4s/blob/main/examples/blaze/src/main/scala/com/example/http4s/blaze/BlazeWebSocketExample.scala
  *
  * TODO - implement tailing a stream, e.g.:
  * $ GET /ws/kafka/<config name>?offset=123 => a stream of that topic's data
  * $ GET /ws/kafka  => awaits a client message for a configuration and then streams that data
  *
  *
  */
object KafkaTailRoute extends Http4sDsl[Task] {
  def apply(wsb: WebSocketBuilder[Task]): HttpRoutes[Task] =
    HttpRoutes.of[Task] {
      case GET -> Root / "hello" =>
        Ok("Hello world.")

      case GET -> Root / "ws" =>
        val toClient: fs2.Stream[Task, WebSocketFrame] =
          fs2.Stream.awakeEvery[Task](1.seconds).map(d => Text(s"Ping! $d"))
        val fromClient: Pipe[Task, WebSocketFrame, Unit] = _.evalMap {
          case Text(t, _) => Task(println(t))
          case f          => Task(println(s"Unknown type: $f"))
        }
        wsb.build(toClient, fromClient)

      case GET -> Root / "wsecho" =>
        val echoReply: Pipe[Task, WebSocketFrame, WebSocketFrame] =
          _.collect {
            case Text(msg, _) => Text("You sent the server: " + msg)
            case _            => Text("Something new")
          }

        Queue
          .unbounded[Task, Option[WebSocketFrame]]
          .flatMap { q =>
            val d: fs2.Stream[Task, WebSocketFrame] = fs2.Stream.fromQueueNoneTerminated(q).through(echoReply)
            val e: Pipe[Task, WebSocketFrame, Unit] = _.enqueueNoneTerminated(q)
            wsb.build(d, e)
          }
    }
}
