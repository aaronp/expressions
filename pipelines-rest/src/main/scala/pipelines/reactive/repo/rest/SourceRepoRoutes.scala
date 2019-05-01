package pipelines.reactive.repo.rest

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import pipelines.reactive.repo._
import pipelines.reactive.{ContentType, SourceRepository}
import pipelines.rest.routes.{BaseCirceRoutes, SecureRouteSettings, SecureRoutes}

case class SourceRepoRoutes(repository: SourceRepository, secureSettings: SecureRouteSettings)
    extends SecureRoutes(secureSettings)
    with SourceRepoEndpoints
    with RepoSchemas
    with BaseCirceRoutes {

  def listSourcesRoute: Route = {
    val wtfb = implicitly[JsonResponse[ListRepoSourcesResponse]]
    sources.list(wtfb).implementedBy { contentTypeOpt =>
      val request = ListRepoSourcesRequest(contentTypeOpt.map(ContentType.apply))
      repository.listSources(request)
    }
  }

  def listTransformsRoute: Route = {
    val wtfb = implicitly[JsonResponse[ListTransformationResponse]]
    transforms.list(wtfb).implementedBy { contentTypeOpt =>
      val request = ListTransformationRequest(contentTypeOpt.map(ContentType.apply))
      repository.listTransforms(request)
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
      val result = repository.allTypes.map(_.toString).sorted
      listEndpoint.response(result)
    }
  }

  def handleRepoRequestRoute: Route = repo.repoEndpoint.implementedBy(repository.handle)

  def routes: Route = {

    handleRepoRequestRoute ~ listSourcesRoute ~ listTransformsRoute ~ listTypesRoute
  }
}
