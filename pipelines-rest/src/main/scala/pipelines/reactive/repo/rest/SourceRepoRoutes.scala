package pipelines.reactive.repo.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pipelines.reactive.repo._
import pipelines.reactive.{ContentType, PipelineService}
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}

case class SourceRepoRoutes(pipelineService: PipelineService, secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with SourceRepoEndpoints
    with RepoSchemas
    with BaseCirceRoutes {

  def listSourcesRoute: Route = {
    val wtf = implicitly[JsonResponse[ListRepoSourcesResponse]]
    extractUri { uri =>
      authenticated { claims =>
        sources.listEndpoint(wtf).implementedBy { _ =>
          val queryParams = uri.query().toMap.mapValues { text =>
            text.replaceAllLiterally("userId", claims.userId).replaceAllLiterally("userName", claims.name)
          }
          pipelineService.listSources(queryParams)
        }
      }
    }
  }

  def listTransformsRoute: Route = {
    val wtf = implicitly[JsonResponse[ListTransformationResponse]]
    transforms.list(wtf).implementedBy { contentTypeOpt =>
      val request = ListTransformationRequest(contentTypeOpt.map(ContentType.apply))
//      repository.listTransforms(request)
      ???
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

  def routes: Route = {

    listSourcesRoute ~ listTransformsRoute ~ listTypesRoute
  }
}
