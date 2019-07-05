package pipelines.reactive.repo

import io.circe.Json

trait RepoSchemas extends endpoints.circe.JsonSchemas {

  implicit def TypesSchema: JsonSchema[Seq[String]] = JsonSchema(implicitly, implicitly)

  implicit def ListSinkRequestSchema: JsonSchema[ListSinkRequest]   = JsonSchema(implicitly, implicitly)
  implicit def ListSinkResponseSchema: JsonSchema[ListSinkResponse] = JsonSchema(implicitly, implicitly)

  implicit def ListRepoSourcesRequestSchema: JsonSchema[ListRepoSourcesRequest]   = JsonSchema(implicitly, implicitly)
  implicit def ListRepoSourcesResponseSchema: JsonSchema[ListRepoSourcesResponse] = JsonSchema(implicitly, implicitly)

  implicit def ListTransformationRequestSchema: JsonSchema[ListTransformationRequest]   = JsonSchema(implicitly, implicitly)
  implicit def ListTransformationResponseSchema: JsonSchema[ListTransformationResponse] = JsonSchema(implicitly, implicitly)

  implicit def CreateRepoSourceRequestSchema: JsonSchema[CreateSourceAliasRequest]   = JsonSchema(implicitly, implicitly)
  implicit def CreateRepoSourceResponseSchema: JsonSchema[CreateSourceAliasResponse] = JsonSchema(implicitly, implicitly)

  implicit def RepoRequestSchema: JsonSchema[RepoRequest]   = JsonSchema(implicitly, implicitly)
  implicit def RepoResponseSchema: JsonSchema[RepoResponse] = JsonSchema(implicitly, implicitly)

  implicit def CirceJsonSchema: JsonSchema[Json] = JsonSchema(implicitly, implicitly)

  implicit def PushSourceResponseSchema: JsonSchema[PushSourceResponse] = JsonSchema(implicitly, implicitly)
}
