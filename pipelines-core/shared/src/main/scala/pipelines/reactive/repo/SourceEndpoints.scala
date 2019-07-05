package pipelines.reactive.repo

import io.circe.Json
import pipelines.core.{BaseEndpoint, GenericMessageResult}

/**
  * Endpoints for working with a repository::
  *
  * GET /repo/source # list sources
  * GET /repo/transform # list transforms
  *
  * POST /repo # handle a [[RepoRequest]]
  */
trait SourceEndpoints extends BaseEndpoint {

  /** list all sources
    * GET /repo/source?type=String
    */
  object findSources {
    def request = get(path / "source" / "list")

    def response(resp: JsonResponse[ListRepoSourcesResponse]): Response[ListRepoSourcesResponse] = jsonResponse[ListRepoSourcesResponse](Option("Lists registered sources"))(resp)

    def listEndpoint(resp: JsonResponse[ListRepoSourcesResponse]): Endpoint[Unit, ListRepoSourcesResponse] = endpoint(request, response(resp))
  }

  /**
    * A means to push (POST) json data at an endpoint.
    *
    * It's probably too much of a pain in the ass to make 'endpoints' also take multipart requests, so I'll manually
    * add a <push source>/upload which takes a multipart
    */
  object pushSource {

    def request(implicit req: JsonRequest[Json]): Request[((String, Option[Boolean], Option[Boolean]), Json)] = {
      val createIfMissing = qs[Option[Boolean]]("createIfMissing", docs = Option("If the push source for this name does not yet exist, then create it"))
      val persistent      = qs[Option[Boolean]]("persistent", docs = Option("If the source was created, this flag specifies that it should be recreated on restart"))
      post(path / "source" / "push" / segment[String]("name") /? (createIfMissing & persistent), jsonRequest[Json]())
    }

    def response(implicit resp: JsonResponse[PushSourceResponse]): Response[PushSourceResponse] =
      jsonResponse[PushSourceResponse](Option("Reports a message on creation"))(resp)

    def pushEndpoint(implicit req: JsonRequest[Json], resp: JsonResponse[PushSourceResponse]): Endpoint[((String, Option[Boolean], Option[Boolean]), Json), PushSourceResponse] = {
      endpoint(request, response)
    }
  }

  /** list the unique types
    * GET /repo/types
    */
  object types {
    type TypesResponse = Seq[String]
    def request: Request[Unit] = get(path / "source" / "types")

    def response(resp: JsonResponse[TypesResponse]): Response[TypesResponse] = jsonResponse[TypesResponse](Option("Lists the possible types"))(resp)

    def list(resp: JsonResponse[TypesResponse]): Endpoint[Unit, TypesResponse] = endpoint(request, response(resp))
  }
}
