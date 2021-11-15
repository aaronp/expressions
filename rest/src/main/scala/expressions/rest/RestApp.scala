package expressions.rest

import cats.effect.kernel.Async
import cats.effect.std.Queue
import com.typesafe.config.{Config, ConfigFactory}
import expressions.rest.RestApp.Settings
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
  * @param settings
  */
case class RestApp(settings: Settings) {

  import settings.*

  class WebsocketExample(wsb: WebSocketBuilder[Task]) extends Http4sDsl[Task] {
    def routes: HttpRoutes[Task] =
      HttpRoutes.of[Task] {
        case GET -> Root / "hello" =>
          Ok("Hello world.")

        case GET -> Root / "ws" =>
          val toClient: fs2.Stream[Task, WebSocketFrame] =
            fs2.Stream.awakeEvery[Task](1.seconds).map(d => Text(s"Ping! $d"))
          val fromClient: Pipe[Task, WebSocketFrame, Unit] = _.evalMap {
            case Text(t, _) => Task(println(t))
            case f => Task(println(s"Unknown type: $f"))
          }
          wsb.build(toClient, fromClient)

        case GET -> Root / "wsecho" =>
          val echoReply: Pipe[Task, WebSocketFrame, WebSocketFrame] =
            _.collect {
              case Text(msg, _) => Text("You sent the server: " + msg)
              case _ => Text("Something new")
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

  private def mkRouter(restRoutes: HttpRoutes[Task])(wsBuilder: WebSocketBuilder[Task]) = {
    val logAction = if (recordRequestResponse) {
      Option(LiveRecorder.recordSession())
    } else {
      None
    }

    val httpApp = Router[Task](
      "/" -> StaticFileRoutes(riffConfig).routes[Task](), //
      "/rest" -> CORS(restRoutes), //
      "/ws" -> WebsocketExample(wsBuilder).routes //
    ).orNotFound

    if (logHeaders || logBody || recordRequestResponse) {
      Logger.httpApp(logHeaders, logBody, logAction = logAction)(httpApp)
    } else httpApp
  }

  def serve(rootConfig: Config): ZIO[zio.ZEnv, Nothing, ExitCode] =
    for {
      rawRoutes <- RestRoutes(rootConfig).orDie
      httpRoutes = mkRouter(rawRoutes)
      exitCode <- BlazeServerBuilder[Task](Async[Task])
        .bindHttp(port, host)
        .withHttpWebSocketApp(httpRoutes)
        .serve
        .compile[Task, Task, cats.effect.ExitCode]
        .drain
        .exitCode
    } yield exitCode
}

object RestApp {

  def apply(rootConfig: Config = ConfigFactory.load()): RestApp = apply(Settings(rootConfig.getConfig("app")))

  def apply(settings: Settings): RestApp = new RestApp(settings)

  case class Settings(riffConfig: Config, host: String, port: Int, logHeaders: Boolean, logBody: Boolean, recordRequestResponse: Boolean)

  object Settings {
    def apply(config: Config): Settings = {
      Settings(
        config,
        host = config.getString("host"),
        port = config.getInt("port"),
        logHeaders = config.getBoolean("logHeaders"),
        logBody = config.getBoolean("logBody"),
        recordRequestResponse = config.getBoolean("recordRequestResponse")
      )
    }

    def fromRootConfig(rootConfig: Config = ConfigFactory.load()): Settings = Settings(rootConfig.getConfig("app"))
  }
}
