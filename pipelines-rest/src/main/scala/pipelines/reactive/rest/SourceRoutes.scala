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

case class SourceRoutes(pipelineService: PipelineService, secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with SourceEndpoints
    with RepoSchemas
    with BaseCirceRoutes {

  def routes: Route = {
    //listTransformsRoute ~
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
              val getOrCreateFuture =
                pipelineService.pushSourceForName[PushEvent](name, createIfMissingOpt.getOrElse(false), persistentOpt.getOrElse(false), RestMain.queryParamsForUri(uri, claims))

              getOrCreateFuture.map {
                case (created, dataSource) =>
                  if (!body.isNull) {
                    dataSource.push(PushEvent(claims.userId, claims.name, body))
                  }

                  if (created) {
                    CreatedPushSourceResponse("Created", dataSource.contentType, dataSource.metadataWithContentType)
                  } else {
                    PushedValueResponse(true)
                  }
              }
          }
        }
      }
    }
  }

  def listTypesRoute: Route = {
    val wtf                                               = implicitly[JsonResponse[types.TypesResponse]]
    val listEndpoint: Endpoint[Unit, types.TypesResponse] = types.list(wtf)
    val authReq = listEndpoint.request.flatMap { _ =>
      authenticated
    }
    authReq { claims =>
      logger.info("Claims is: " + claims)
      val result = ??? //repository.allTypes.map(_.toString).sorted
      listEndpoint.response(result)
    }
  }
}
