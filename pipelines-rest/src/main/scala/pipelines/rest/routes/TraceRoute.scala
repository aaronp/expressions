package pipelines.rest.routes

import akka.http.scaladsl.model.{HttpHeader, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.directives.BasicDirectives.{extractRequestContext, mapRouteResult}
import akka.http.scaladsl.server.{RouteResult, _}
import com.typesafe.scalalogging.StrictLogging

/**
  * A route which can wrap incoming/outgoing messages for support, metrics, etc.
  */
trait TraceRoute {
  def onRequest(request: HttpRequest): Unit
  def onResponse(request: HttpRequest, requestTime: Long, response: RouteResult): Unit

  final def wrap: Directive0 = extractRequestContext.tflatMap { ctxt =>
    onRequest(ctxt._1.request)
    val started = System.currentTimeMillis
    mapRouteResult { result: RouteResult =>
      onResponse(ctxt._1.request, started, result)
      result
    }
  }

}

object TraceRoute {

  private val BlackListFmtHeader = Set("Last-Modified", "ETag")
  private def fmt(headers: Seq[HttpHeader]) = {
    headers.filterNot(h => BlackListFmtHeader.contains(h.name)) map { h =>
      s"${h.name}:${h.value}"
    }
  }
  def pretty(response: RouteResult) = {
    response match {
      case Complete(HttpResponse(status, headers, entity, _)) if status.intValue == 200 =>
        fmt(headers).mkString(s"${entity.getContentType} OK : [", ",", "]")
      case Complete(HttpResponse(status, headers, entity, _)) =>
        fmt(headers).mkString(s"status=${status.intValue} (${status.reason}) ${entity.getContentType} : [", ",", "]")
      case other => other.toString
    }
  }
  def pretty(r: HttpRequest) = {
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
    val BlackList = Set("/js/", "site.webmanifest", "favicon", ".css")
    override def onRequest(request: HttpRequest): Unit = {
      val uriStr = request.uri.toString
      if (!BlackList.exists(uriStr.contains)) {
        logger.debug(s"onRequest(${pretty(request)})")
      } else {
        logger.trace(s"onRequest(${pretty(request)})")
      }
//      val detail = request.headers.mkString(s"${pretty(request)} ${request.headers.size} HEADERS: \n\t", "\n\t", "\n")
//      logger.debug(detail)

    }

    private val blacklist = Set(".js", ".html", ".css", ".webmanifest", ".png")
    override def onResponse(request: HttpRequest, requestTime: Long, response: RouteResult): Unit = {
      val diff         = System.currentTimeMillis - requestTime
      val responseText = pretty(response)
      val input        = pretty(request)
      if (blacklist.exists(input.contains)) {
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
