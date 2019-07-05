package pipelines.reactive.repo

import pipelines.core.BaseEndpoint

trait TransformEndpoints extends BaseEndpoint {


  /** list transformations
    * GET /repo/transform?inputType=Int
    */
  object listTransforms {

    def request: Request[Option[String]] = get(path / "repo" / "transform" /? qs[Option[String]]("inputType"))

    def response(resp: JsonResponse[ListTransformationResponse]): Response[ListTransformationResponse] =
      jsonResponse[ListTransformationResponse](Option("Lists registered transformations"))(resp)

    def list(resp: JsonResponse[ListTransformationResponse]): Endpoint[Option[String], ListTransformationResponse] = endpoint(request, response(resp))
  }

}
