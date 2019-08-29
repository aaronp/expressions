package pipelines.rest

import akka.NotUsed
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.RouteResult._
import akka.http.scaladsl.settings.RoutingSettings
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import pipelines.rest.routes.TraceRoute
import pipelines.ssl.SSLConfig

import scala.concurrent.Future
import scala.util.Try

/**
  * Represents our started REST service
  *
  * @param settings
  * @param service the service which is represented (wrapped) by this running service
  * @param bindingFuture
  * @tparam A
  */
class RunningServer[A] private (val settings: RestSettings, val service: A, val bindingFuture: Future[Http.ServerBinding]) extends AutoCloseable {
  override def close(): Unit = settings.env.close()
}

object RunningServer extends StrictLogging {

  def start[A](settings: RestSettings, sslConf: SSLConfig, service: A, httpsRoutes: Route): RunningServer[A] = {
    val https = Option(loadHttps(sslConf).get) // let it throw, let it throw! can't hold me back anymore...
    start[A](settings, https, service, httpsRoutes)
  }

  def start[A](settings: RestSettings, https: Option[HttpsConnectionContext], service: A, httpsRoutes: Route): RunningServer[A] = {
    import settings.env._
    val flow: Flow[HttpRequest, HttpResponse, NotUsed] = route2HandlerFlow(httpsRoutes)
    val http                                           = Http()
    val connectionContext                              = https.getOrElse(http.defaultServerHttpContext)
    val httpsBindingFuture                             = http.bindAndHandle(flow, settings.host, settings.port, connectionContext = connectionContext)
    val bindingFuture                                  = httpsBindingFuture
    new RunningServer(settings, service, bindingFuture)
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
