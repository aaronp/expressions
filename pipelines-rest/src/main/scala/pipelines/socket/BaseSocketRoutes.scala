package pipelines.socket

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import com.typesafe.scalalogging.StrictLogging
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import pipelines.rest.routes.{SecureRouteSettings, SecureRoutes}

/** The concept is that two different web-socket routes could request a socket.
  *
  * As long as each route can connect to an observable then the single socket will be reused, provided the messages are sent by [[AddressedMessage]]
  *
  */
abstract class BaseSocketRoutes(val settings: SecureRouteSettings, private val cache: BaseSocketRoutes.SocketCache = new BaseSocketRoutes.SocketCache)
    extends SecureRoutes(settings) {

  type NewSocket    = Left[ServerSocket, ServerSocket]
  type CachedSocket = Right[ServerSocket, ServerSocket]

  protected def timestamp(now: ZonedDateTime = ZonedDateTime.now()): String = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(now)

  /**
    * Gets a socket for some id
    *
    * @param settings some unique id
    * @return a monix socket
    */
  def extractSocket(settings: SocketSettings): Directive[(Scheduler, Either[ServerSocket, ServerSocket])] = {
    extractExecutionContext.map { ec =>
      implicit val sched = Scheduler.apply(ec)
      sched -> getOrCreateSocket(settings)
    }
  }

  def getSocket(settings: SocketSettings)(implicit sched: Scheduler): ServerSocket = {
    cache.apply(settings)
  }

  protected def defaultSettings(id: String): SocketSettings = {
    SocketSettings(id, id)
  }

  def getOrCreateSocket(settings: SocketSettings)(implicit sched: Scheduler): Either[ServerSocket, ServerSocket] = {
    cache.socketFor(settings)
  }

  def withSocketRoute(settings: SocketSettings)(f: (Scheduler, ServerSocket) => Unit): Route = {
    extractSocket(settings) {
      case (sched, Left(newSocket: ServerSocket)) =>
        f(sched, newSocket)

        val flow = newSocket.akkaFlow
        handleWebSocketMessages(flow)

      case (sched, Right(existingSocket)) =>
        f(sched, existingSocket)
        complete(settings.id)
    }
  }

}

object BaseSocketRoutes {

  class SocketCache() extends StrictLogging {
    private[this] var byName = Map[String, ServerSocket]()
    private[this] object StateLock
    def apply(settings: SocketSettings)(implicit sched: Scheduler): ServerSocket = {
      socketFor(settings) match {
        case Left(s)  => s
        case Right(s) => s
      }
    }

    def remove(id: String) = {
      StateLock.synchronized {
        logger.info(s"removing socket $id")
        byName = byName - id
      }
    }
    def socketFor(settings: SocketSettings)(implicit sched: Scheduler): Either[ServerSocket, ServerSocket] = {
      StateLock.synchronized {
        byName.get(settings.id) match {
          case Some(existing) => Right(existing)
          case None =>
            val base: ServerSocket                      = ServerSocket(settings)
            val newOutput: Observable[AddressedMessage] = base.output.guarantee(Task.eval(remove(settings.id)))
            val instance                                = base.withOutput(newOutput)

            byName = byName.updated(settings.id, instance)
            Left(instance)
        }
      }
    }
  }
}
