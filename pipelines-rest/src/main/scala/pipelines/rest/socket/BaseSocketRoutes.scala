package pipelines.rest.socket

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import monix.execution.Scheduler
import pipelines.rest.routes.{SecureRouteSettings, SecureRoutes}

/** The concept is that two different web-socket routes could request a socket.
  *
  * As long as each route can connect to an observable then the single socket will be reused, provided the messages are sent by [[AddressedMessage]]
  *
  */
abstract class BaseSocketRoutes(val settings: SecureRouteSettings) extends SecureRoutes(settings) {

  type NewSocket    = Left[ServerSocket, ServerSocket]
  type CachedSocket = Right[ServerSocket, ServerSocket]

  protected def timestamp(now: ZonedDateTime = ZonedDateTime.now()): String = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now)

  /**
    * Gets a socket for some id
    *
    * @param settings some unique id
    * @return a monix socket
    */
  def extractSocket(settings: SocketSettings): Directive[(Scheduler, ServerSocket)] = {
    extractExecutionContext.map { ec =>
      implicit val sched = Scheduler.apply(ec)
      sched -> ServerSocket(settings)
    }
  }

  protected def defaultSettings(id: String): SocketSettings = SocketSettings(id, id)

  def withSocketRoute(settings: SocketSettings)(f: (Scheduler, ServerSocket) => Unit): Route = {
    extractSocket(settings) {
      case (sched, newSocket: ServerSocket) =>
        f(sched, newSocket)
        val flow = newSocket.akkaFlow
        handleWebSocketMessages(flow)
    }
  }

}
