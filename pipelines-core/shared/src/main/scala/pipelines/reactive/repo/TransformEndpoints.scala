package pipelines.reactive.repo

import pipelines.core.BaseEndpoint

/**
  * Endpoints for transformations.
  *
  * Some we could adjust in-flight (e.g. change the rate of a rate-limiting transform),
  * or just query which transforms exist.
  *
  * Transforms typically are all known and configured statically on start-up, though conceptually we could do the
  * same thing with transforms as we do with compiled filters.
  */
trait TransformEndpoints extends BaseEndpoint {

  /** list transformations
    * GET /repo/transform?inputType=Int
    */
  object listTransforms {

    val queryString                                        = qs[Option[String]]("inputType") & qs[Option[String]]("outputType")
    def request: Request[(Option[String], Option[String])] = get(path / "transform" / "find" /? queryString)

    def response(implicit resp: JsonResponse[ListTransformationResponse]): Response[ListTransformationResponse] =
      jsonResponse[ListTransformationResponse](Option("Lists registered transformations"))(resp)

    def list(implicit resp: JsonResponse[ListTransformationResponse]): Endpoint[(Option[String], Option[String]), ListTransformationResponse] = endpoint(request, response(resp))
  }

}
