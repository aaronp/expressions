package pipelines.reactive.repo

import pipelines.core.BaseEndpoint

/**
  * Endpoints for working with a repository::
  *
  * GET /repo/source # list sources
  * GET /repo/transform # list transforms
  *
  * POST /repo # handle a [[RepoRequest]]
  */
trait SourceRepoEndpoints extends BaseEndpoint {

  /** list all sources
    * GET /repo/source?type=String
    */
  object sources {
    def request: Request[Option[String]] = get(path / "repo" / "source" /? qs[Option[String]]("type"))

    def response(resp: JsonResponse[ListRepoSourcesResponse]): Response[ListRepoSourcesResponse] = jsonResponse[ListRepoSourcesResponse](Option("Lists registered sources"))(resp)

    def list(resp: JsonResponse[ListRepoSourcesResponse]): Endpoint[Option[String], ListRepoSourcesResponse] = endpoint(request, response(resp))
  }

  /** list the unique types
    * GET /repo/types
    */
  object types {
    type TypesResponse = Seq[String]
    def request: Request[Unit] = get(path / "repo" / "types")

    def response(resp: JsonResponse[TypesResponse]): Response[TypesResponse] = jsonResponse[TypesResponse](Option("Lists the possible types"))(resp)

    def list(resp: JsonResponse[TypesResponse]): Endpoint[Unit, TypesResponse] = endpoint(request, response(resp))
  }

  /** list transformations
    * GET /repo/transform?inputType=Int
    */
  object transforms {

    def request: Request[Option[String]] = get(path / "repo" / "transform" /? qs[Option[String]]("inputType"))

    def response(resp: JsonResponse[ListTransformationResponse]): Response[ListTransformationResponse] =
      jsonResponse[ListTransformationResponse](Option("Lists registered transformations"))(resp)

    def list(resp: JsonResponse[ListTransformationResponse]): Endpoint[Option[String], ListTransformationResponse] = endpoint(request, response(resp))
  }

}
