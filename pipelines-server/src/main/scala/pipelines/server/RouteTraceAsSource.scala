package pipelines.server

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.RouteResult
import com.typesafe.scalalogging.StrictLogging
import monix.execution.Scheduler
import pipelines.reactive.{DataSource, PipelineService}
import pipelines.rest.routes.TraceRoute

object RouteTraceAsSource extends StrictLogging {

  /** @param pipelinesService
    * @param ioScheduler
    * @return a TraceRoute which exposes the REST requests/responses as sources
    */
  def apply(pipelinesService: PipelineService)(implicit ioScheduler: Scheduler): TraceRoute = {

    def addSrc[A](src: DataSource.PushSource[A]) = {
      val Seq(created: DataSource.PushSource[A]) = pipelinesService.getOrCreateSource(src)
      pipelinesService.getOrCreateSource(created).ensuring(_.head == created, "getOrCreate should've returned the same source")
      created
    }

    val usersRequests: DataSource.PushSource[HttpRequest] = {
      addSrc(
        DataSource
          .createPush[HttpRequest]
          .apply(ioScheduler) //
          .addMetadata("label", "http-requests"))
    }
    val usersResponses: DataSource.PushSource[(HttpRequest, Long, RouteResult)] = {
      addSrc(
        DataSource
          .createPush[(HttpRequest, Long, RouteResult)]
          .apply(ioScheduler) //
          .addMetadata("label", "http-request-response"))
    }
    TraceRoute
      .onRequest { req: HttpRequest =>
        try {
          TraceRoute.log.onRequest(req)
          usersRequests.push(req)
        } catch {
          case e: Throwable =>
            logger.error("monika : " + e, e)
        }
      }
      .onResponse {
        case tuple @ (req, duration, resp) =>
          try {
            TraceRoute.log.onResponse(req, duration, resp)
            usersResponses.push(tuple)
          } catch {
            case e: Throwable =>
              logger.error("bang : " + e, e)
          }
      }
  }
}
