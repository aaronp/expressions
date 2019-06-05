package pipelines.client

import endpoints.algebra.Documentation
import endpoints.xhr
import org.scalajs.dom
import org.scalajs.dom.XMLHttpRequest
import pipelines.core.Redirection
import pipelines.manual.PushEndpoints
import pipelines.reactive.repo.{ListRepoSourcesResponse, RepoSchemas, SourceRepoEndpoints}
import pipelines.users.{LoginEndpoints, LoginResponse, UserSchemas}

import scala.concurrent.Future
import scala.scalajs.js

object PipelinesXhr extends xhr.future.Endpoints with xhr.circe.JsonSchemaEntities with LoginEndpoints with SourceRepoEndpoints with RepoSchemas with UserSchemas with PushEndpoints {

  def onLogin(response: LoginResponse) = {
    dom.window.console.log(s"got response $response")
    state = state.withResponse(response)
  }

  private var state = {
    dom.window.console.log("Creating new app state")
    AppState()
  }

  def listSources(contentType: Option[String]): Future[ListRepoSourcesResponse] = {
    sources.list(ListRepoSourcesResponseSchema).apply(contentType)
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
                              tags: List[String]): PipelinesXhr.Endpoint[A, B] = {

    val wrappedRequest = new Request[A] {
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
      override def href(a: A): String = originalRequest.href(a)
    }

    super.endpoint(wrappedRequest, wrapResponse(response), summary, description, tags)
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
