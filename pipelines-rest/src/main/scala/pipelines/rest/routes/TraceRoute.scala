package pipelines.rest.routes

import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.directives.BasicDirectives.{extractRequestContext, mapRouteResult}
import akka.http.scaladsl.server.{RouteResult, _}
import com.typesafe.scalalogging.StrictLogging
import pipelines.reactive.Transform
import pipelines.rest.socket.AddressedMessage

/**
  * A route which can wrap incoming/outgoing messages for support, metrics, etc.
  */
trait TraceRoute {
  def onRequest(request: HttpRequest): Unit
  def onResponse(request: HttpRequest, delta: Long, response: RouteResult): Unit

  final def wrap: Directive0 = extractRequestContext.tflatMap { ctxt =>
    onRequest(ctxt._1.request)
    val started = System.currentTimeMillis
    mapRouteResult { result: RouteResult =>
      onResponse(ctxt._1.request, System.currentTimeMillis - started, result)
      result
    }
  }

}

object TraceRoute {

  private val BlackListFmtHeader = Set("Last-Modified", "ETag", "X-Access-Token")

  /**
    * Expose these trace routes as [[AddressedMessage]] so that we can see 'em over a socket
    *
    * @param request
    * @return
    */
  def httpRequestAsAddressedMessage(request: HttpRequest): AddressedMessage = {
    AddressedMessage(pretty(request))
  }

  val httpRequestTransform = Transform.map[HttpRequest, AddressedMessage](TraceRoute.httpRequestAsAddressedMessage)

  private def fmt(headers: Seq[HttpHeader]) = {
    headers.filterNot(h => BlackListFmtHeader.contains(h.name)) map { h =>
      s"${h.name}:${h.value}"
    }
  }
  def pretty(response: RouteResult): String = {
    response match {
      case Complete(HttpResponse(status, headers, entity, _)) if status.intValue == 200 =>
        fmt(headers).mkString(s"${entity.getContentType} OK : [", ",", "]")
      case Complete(HttpResponse(status, headers, entity, _)) =>
        fmt(headers).mkString(s"status=${status.intValue} (${status.reason}) ${entity.getContentType} : [", ",", "]")
      case other => other.toString
    }
  }
  def pretty(r: HttpRequest): String = {
    val token = r.headers
      .find(_.name.toLowerCase.contains("token"))
      .map { h =>
        s"[${h.name} : ${h.value}]"
      }
      .getOrElse("")
    s"${r.method.value} $token ${r.uri}"
  }

  /**
    * A simple logging TraceRoute
    *
    * {{{
    *   val route : Route = TraceRoute.log.wrap(myRoute)
    * }}}
    */
  object log extends TraceRoute with StrictLogging {
    val RequestBlackList = Set("/js/", "/jslib/", "site.webmanifest", "favicon", ".css", ".html")
    override def onRequest(request: HttpRequest): Unit = {
      val uriStr = request.uri.toString
      if (!RequestBlackList.exists(uriStr.contains)) {
        logger.debug(s"onRequest(${pretty(request)})")
      } else {
        logger.trace(s"onRequest(${pretty(request)})")
      }
//      val detail = request.headers.mkString(s"${pretty(request)} ${request.headers.size} HEADERS: \n\t", "\n\t", "\n")
//      logger.debug(detail)

    }

    private val ResponseBlacklist = Set(".js", ".html", ".css", ".webmanifest", ".png", ".ico")
    override def onResponse(request: HttpRequest, diff: Long, response: RouteResult): Unit = {
      val responseText = pretty(response)
      val input        = pretty(request)
      if (ResponseBlacklist.exists(input.contains)) {
        logger.trace(s"${input} took ${diff}ms to produce $responseText")
      } else {
        logger.info(s"${input} took ${diff}ms to produce $responseText")
      }
    }
  }

  /** @param requestCallback the request callback
    * @param responseCallback the response callback
    * @return a TraceRoute instance
    */
  def apply(requestCallback: HttpRequest => Unit, responseCallback: (HttpRequest, TimestampMillis, RouteResult) => Unit) = new TraceRoute {
    override def onRequest(request: HttpRequest): Unit = {
      requestCallback(request)
    }

    override def onResponse(request: HttpRequest, requestTime: TimestampMillis, response: RouteResult): Unit = {
      responseCallback(request, requestTime, response)
    }
  }

  /**
    * DSL for creating a TraceRoute handler
    *
    * @param callback the request callback
    * @return a TraceRequest instance which can be uses as a DSL to expose an 'onResponse' function in order to build a [[TraceRoute]]
    */
  def onRequest(callback: HttpRequest => Unit): TraceRequest = TraceRequest(callback)
}
