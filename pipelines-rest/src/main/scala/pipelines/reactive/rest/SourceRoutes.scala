package pipelines.reactive.rest

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.circe.Json
import pipelines.reactive._
import pipelines.reactive.repo._
import pipelines.rest.RestMain
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}
import pipelines.users.Claims

import scala.concurrent.Future

case class SourceRoutes(pipelineService: PipelineService, secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with SourceEndpoints
    with RepoSchemas
    with BaseCirceRoutes {

  def routes: Route = {
    pushSourceRoute ~ findSourcesRoute ~ listTypesRoute
  }

  def findSourcesRoute: Route = {
    val wtf = implicitly[JsonResponse[ListRepoSourcesResponse]]
    extractUri { uri: Uri =>
      authenticated { claims: Claims =>
        findSources.listEndpoint(wtf).implementedBy { _ =>
          val queryParams: Map[String, String] = RestMain.queryParamsForUri(uri, claims)
          pipelineService.listSources(queryParams)
        }
      }
    }
  }
  def pushSourceRoute: Route = {
    val wtf  = implicitly[JsonRequest[Json]]
    val wtf2 = implicitly[JsonResponse[PushSourceResponse]]
    extractUri { uri =>
      extractExecutionContext { implicit ec =>
        authenticated { claims =>
          pushSource.pushEndpoint(wtf, wtf2).implementedByAsync {
            case ((name, createIfMissingOpt, persistentOpt), body) =>
              val getOrCreateFuture: Future[(Boolean, DataSource.PushSource[PushEvent])] = {
                val create   = createIfMissingOpt.getOrElse(false)
                val persist  = persistentOpt.getOrElse(false)
                val metadata = RestMain.queryParamsForUri(uri, claims)
                pipelineService.pushSourceForName[PushEvent](name, create, persist, metadata)
              }

              getOrCreateFuture.map {
                case (true, dataSource) =>
                  if (!body.isNull) {
                    dataSource.push(PushEvent(claims, body))
                  }
                  CreatedPushSourceResponse(name, dataSource.contentType, dataSource.metadataWithContentType)
                case (false, dataSource) =>
                  val ok = !body.isNull
                  if (!body.isNull) {
                    dataSource.push(PushEvent(claims, body))
                  }
                  PushedValueResponse(ok)
              }
          }
        }
      }
    }
  }

  def listTypesRoute: Route = {
    val wtf = implicitly[JsonResponse[types.TypesResponse]]
    authenticated { claims =>
      logger.info("Claims is: " + claims)
      types.list(wtf).implementedBy { _ =>
        pipelineService.sources.list().map(_.contentType.toString).distinct
      }
    }
  }
}
