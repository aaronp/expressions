package pipelines.client

import endpoints.algebra.Documentation
import endpoints.xhr
import io.circe.Json
import org.scalajs.dom
import org.scalajs.dom.XMLHttpRequest
import pipelines.core.{CoreSchemas, Redirection}
import pipelines.reactive.repo.{ListRepoSourcesResponse, PushSourceResponse, RepoSchemas, SourceEndpoints, TransformEndpoints}
import pipelines.rest.socket.SocketEndpoint
import pipelines.users.{LoginEndpoints, UserEndpoints, UserRoleEndpoints, UserSchemas}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js

/**
  * The glue to our 'Endpoints' from XHR -- with a few convenience methods/functions thrown in.
  *
  * In particular we wrap the requests to ensure we use the JWT from our local storage (and also populate local storage
  * on login)
  */
object PipelinesXhr
    extends xhr.future.Endpoints
    with xhr.circe.JsonSchemaEntities
    with LoginEndpoints
    with UserEndpoints
    with UserRoleEndpoints
    with SourceEndpoints
    with TransformEndpoints
    with SocketEndpoint
    with RepoSchemas
    with UserSchemas
    with CoreSchemas {

  def execContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  object implicits {
    implicit val ec = execContext
  }

  /**
    * dom.window.location.host -> localhost:80
    * dom.window.location.hostname -> localhost
    * dom.window.location.origin -> https://localhost:80
    *
    */
  def createSocket(): Future[ClientSocketState] = socketState
  private lazy val socketState: Future[ClientSocketState] = {
    socketTokens.newToken.apply().map { tempToken =>
      ClientSocketState(sockets.request.href(), tempToken)
    }
  }

  private implicit def asRichEndpoint[A, B](ep: PipelinesXhr.Endpoint[A, B]) = new {
    def wrapped(): WrappedEndpoint[A, B] = ep match {
      case wrapped: WrappedEndpoint[A, B] => wrapped
      case other                          => sys.error(s"endpoint was $other")
    }
  }

  def createSource(name: String, persist: Boolean, metadata: Map[String, String]): Future[PushSourceResponse] = {
    val createIfMissing = Option(true)
    pushSource.pushEndpoint.wrapped.addParams(metadata).apply((name, createIfMissing, Option(persist)), Json.Null)
  }

  def listSources(queryParams: Map[String, String]): Future[ListRepoSourcesResponse] = {
    val endpoint = findSources.listEndpoint(ListRepoSourcesResponseSchema).wrapped.addParams(queryParams)
    endpoint()
  }

  def listAllTypes(): Future[PipelinesXhr.types.TypesResponse] = {
    types.list(TypesSchema).apply()
  }

  /**
    * Override the endpoint definition to ensure we set the JWT headers
    *
    * @param originalRequest
    * @param response
    * @param summary
    * @param description
    * @param tags
    * @tparam A
    * @tparam B
    * @return
    */
  override def endpoint[A, B](originalRequest: PipelinesXhr.Request[A],
                              response: js.Function1[XMLHttpRequest, Either[Exception, B]],
                              summary: Documentation,
                              description: Documentation,
                              tags: List[String]): WrappedEndpoint[A, B] = {

    val wrappedRequest = WrappedRequest[A, B](originalRequest, response, summary, description, tags)
    WrappedEndpoint(wrappedRequest, wrapResponse(response), summary, description, tags)
  }

  case class WrappedRequest[A, B](originalRequest: PipelinesXhr.Request[A],
                                  response: js.Function1[XMLHttpRequest, Either[Exception, B]],
                                  summary: Documentation,
                                  description: Documentation,
                                  tags: List[String],
                                  additionalQueryParams: Map[String, String] = Map.empty)
      extends Request[A] {
    override def apply(a: A): (XMLHttpRequest, Option[js.Any]) = {
      val request @ (xhr, _) = originalRequest(a)
      //xhr.setRequestHeader("Content-Type", "application/json")
      AppState.get().currentToken.foreach { jwtToken =>
        xhr.setRequestHeader("Authorization", s"Bearer $jwtToken")
        xhr.setRequestHeader("X-Access-Token", jwtToken)
      }

      request
    }
    override def href(a: A): String = {
      val url = originalRequest.href(a)

      url
    }
  }

  case class WrappedEndpoint[A, B](
      request: WrappedRequest[A, B],
      response: Response[B],
      summary: Documentation,
      description: Documentation,
      tags: List[String]
  ) extends Endpoint[A, B](request) {
    def addParams(queryParams: Map[String, String]): WrappedEndpoint[A, B] = {
      withParams(request.additionalQueryParams ++ queryParams)
    }
    def withParams(queryParams: Map[String, String]): WrappedEndpoint[A, B] = {
      copy(request = request.copy(additionalQueryParams = request.additionalQueryParams ++ queryParams))
    }
    override def apply(a: A) = {
      val promise = Promise[B]()
      performXhr(request, response, a)(
        _.fold(exn => { promise.failure(exn); () }, b => { promise.success(b); () }),
        xhr => { promise.failure(new Exception(xhr.responseText)); () }
      )
      promise.future
    }
  }

  def wrapResponse[B](response: js.Function1[XMLHttpRequest, Either[Exception, B]])(xhr: XMLHttpRequest) = {
    val result: Either[Exception, B] = response(xhr)

    xhr.status match {
      case 401 =>
        import io.circe.parser._
        decode[Redirection](xhr.responseText) match {
          case Right(Redirection(redirectTo)) =>
            dom.window.console
              .log(
                s"Got a 401 w/ a redirection body to '${redirectTo}' from href=${dom.window.location.href}, pathname=${dom.window.location.pathname}, ${dom.window.location.search}")
            dom.window.location.replace(s"$redirectTo&redirectTo=${dom.window.location.href}")
            result
          case Left(cantParseBody) =>
            dom.window.console.log(s"Couldn't parse body '${cantParseBody}' from '${xhr.responseText}'")
            Left(new Exception(s"Got unauthorized response, but couldn't parse '${xhr.responseText}'"))
        }
      case _ => result
    }
  }
}
