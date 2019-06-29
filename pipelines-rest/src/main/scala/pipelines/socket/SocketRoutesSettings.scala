package pipelines.socket

import akka.http.scaladsl.server.Route
import args4c.implicits._
import com.typesafe.config.Config
import pipelines.Env
import pipelines.rest.routes.SecureRouteSettings
import pipelines.users.jwt.Claims

import scala.concurrent.duration._

case class SocketRoutesSettings(secureSettings: SecureRouteSettings, env: Env, socketHandler: (Claims, ServerSocket) => Unit) {

  def socketRoutes = new SocketRoutes(secureSettings, socketHandler)

  def routes: Route = socketRoutes.routes
}

object SocketRoutesSettings {

  def socketHeartbeatFrequency(rootConfig: Config): FiniteDuration = {
    rootConfig.asFiniteDuration("pipelines.socket.heartbeatFrequency")
  }

  def apply(rootConfig: Config, secureSettings: SecureRouteSettings, env: Env): SocketRoutesSettings = {
    val hbFreq = socketHeartbeatFrequency(rootConfig)
    val handler: (Claims, ServerSocket) => Unit = if (rootConfig.hasPath("pipelines.echoSocket")) {
      def handle(c: Claims, s: ServerSocket) = {
        onSocket(hbFreq, c, s)
        echo(c, s)
      }
      handle
    } else {
      onSocket(hbFreq, _, _)
    }
    new SocketRoutesSettings(secureSettings, env, handler)
  }

  def echo(user: Claims, socket: ServerSocket): Unit = {
    val obs = socket.fromRemote.map {
      case AddressedTextMessage(to, txt) => AddressedMessage(to, "echo: " + txt)
      case other                         => other
    }
    obs.subscribe(socket.toRemote)(socket.scheduler)
  }

  def onSocket(socketHeartbeatFrequency: FiniteDuration, user: Claims, socket: ServerSocket): String = {
    if (socketHeartbeatFrequency > 0.millis) {
      import AddressedMessage._
      val heartbeats = heartbeat() +: socket.output.map(_ => heartbeat())
      heartbeats.debounce(socketHeartbeatFrequency).subscribe(socket.toRemote)(socket.scheduler)
    }
    user.name
  }
}
