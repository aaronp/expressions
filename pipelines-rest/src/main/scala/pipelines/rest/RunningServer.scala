package pipelines.rest

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import pipelines.rest.openapi.DocumentationRoutes
import pipelines.rest.routes.TraceRoute
import pipelines.ssl.SSLConfig

import scala.concurrent.Future
import scala.util.Try

/**
  * Represents our started REST service
  *
  * @param settings
  * @param bindingFuture
  */
class RunningServer private (val settings: Settings, val bindingFuture: Future[Http.ServerBinding]) extends AutoCloseable {
  override def close(): Unit = settings.env.close()
}

object RunningServer extends StrictLogging {

  def apply(settings: Settings, sslConf: SSLConfig, httpsRoutes: Route): RunningServer = {
    import settings.env._
    val https: HttpsConnectionContext                  = loadHttps(sslConf).get // let it throw, let it throw! can't hold me back anymore...
    val flow: Flow[HttpRequest, HttpResponse, NotUsed] = route2HandlerFlow(httpsRoutes)
    val httpsBindingFuture                             = Http().bindAndHandle(flow, settings.host, settings.port, connectionContext = https)
    val bindingFuture                                  = httpsBindingFuture
    new RunningServer(settings, bindingFuture)
  }

  def routeFor(settings: Settings, routes: Seq[Route]): Route = {
    import settings.env._
    val theRest = settings.staticRoutes.route +:
      DocumentationRoutes.route +:
      routes

    makeRoutes(theRest)
  }

  def reduce(routes: Seq[Route]): Route = {
    import akka.http.scaladsl.server.Directives._
    routes.reduce(_ ~ _)
  }

  def makeRoutes(routes: Seq[Route])(implicit routingSettings: RoutingSettings): Route = {
    // TODO - instead of just logging, actually write down the requests/responses and expose through the front-end.
    Route.seal(TraceRoute.log.wrap(reduce(routes)))
  }

  def loadHttps(sslConf: SSLConfig): Try[HttpsConnectionContext] = sslConf.newContext().map(ctxt => ConnectionContext.https(ctxt))
}
