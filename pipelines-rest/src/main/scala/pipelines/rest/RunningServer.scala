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

class RunningServer private (val settings: Settings, val bindingFuture: Future[Http.ServerBinding]) extends AutoCloseable {
  override def close(): Unit = settings.env.close()
}

object RunningServer extends StrictLogging {

  def apply(settings: Settings, sslConf: SSLConfig, otherRoutes: Route*): RunningServer = {
    import settings.env._

    val httpsRoutes: Route = {
      val theRest = settings.staticRoutes.route +:
        DocumentationRoutes.route +:
        settings.repoRoutes +:
        otherRoutes

      makeRoutes(settings.userRoutes(sslConf).routes, theRest: _*)
    }
    val https: HttpsConnectionContext = loadHttps(sslConf).get

    logger.warn(s"Starting with\n${settings}\n\n")

    val flow: Flow[HttpRequest, HttpResponse, NotUsed] = route2HandlerFlow(httpsRoutes)
    val httpsBindingFuture                             = Http().bindAndHandle(flow, settings.host, settings.port, connectionContext = https)
    val bindingFuture                                  = httpsBindingFuture
    new RunningServer(settings, bindingFuture)
  }

  def makeRoutes(first: Route, theRest: Route*)(implicit routingSettings: RoutingSettings): Route = {
    val route: Route = {
      import akka.http.scaladsl.server.Directives._

      theRest.foldLeft(first)(_ ~ _)
    }

    // TODO - instead of just logging, actually write down the requests/responses and expose through the front-end.
    // perhaps just through kafka like any other topic
    Route.seal(TraceRoute.log.wrap(route))
  }

  def loadHttps(sslConf: SSLConfig): Try[HttpsConnectionContext] = sslConf.newContext().map(ctxt => ConnectionContext.https(ctxt))
}
