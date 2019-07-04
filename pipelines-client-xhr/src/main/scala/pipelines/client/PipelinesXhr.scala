package pipelines.client

import endpoints.algebra.Documentation
import endpoints.xhr
import org.scalajs.dom
import org.scalajs.dom.XMLHttpRequest
import pipelines.core.{CoreSchemas, Redirection}
import pipelines.manual.PushEndpoints
import pipelines.reactive.repo.{ListRepoSourcesResponse, RepoSchemas, SourceRepoEndpoints}
import pipelines.users.{LoginEndpoints, LoginResponse, UserEndpoints, UserRoleEndpoints, UserSchemas}

import scala.concurrent.{Future, Promise}
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
    with SourceRepoEndpoints
    with RepoSchemas
    with UserSchemas
    with CoreSchemas
    with PushEndpoints {

  def onLogin(response: LoginResponse) = {
    state = state.withResponse(response)
  }

  private var state = {
    dom.window.console.log("Creating new app state")
    AppState()
  }

  def listSources(queryParams: Map[String, String]): Future[ListRepoSourcesResponse] = {
    val endpoint = sources.listEndpoint(ListRepoSourcesResponseSchema) match {
      case wrapped: WrappedEndpoint[Unit, ListRepoSourcesResponse] =>
        wrapped.copy(request = wrapped.request.copy(additionalQueryParams = queryParams))
      case other => sys.error(s"endpoint was $other")
    }
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
      dom.window.console.log(s"Making request $a, token is ${state.currentToken}")
      //xhr.setRequestHeader("Content-Type", "application/json")
      state.currentToken.foreach { jwtToken =>
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
    dom.window.console.log(s"xhr status '${xhr.statusText}' (code='${xhr.status}') resp '${xhr.responseText}' returned ${result}")

    dom.window.console.log(s"href=${dom.window.location.href}")
    dom.window.console.log(s"search=${dom.window.location.search}")
    dom.window.console.log(s"location=${dom.window.location}")
    dom.window.console.log(s"pathname=${dom.window.location.pathname}")
    dom.window.console.log(s"host=${dom.window.location.host}")
    dom.window.console.log(s"hash=${dom.window.location.hash}")
    dom.window.console.log(s"origin=${dom.window.location.origin}")

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
