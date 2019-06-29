package pipelines.client

import org.scalajs.dom
import org.scalajs.dom.document
import org.scalajs.dom.raw.{KeyboardEvent, Node}
import pipelines.core.ParseRedirect
import pipelines.users.{LoginRequest, LoginResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

/**
  */
object PipelinesApp extends HtmlUtils {

  @JSExportTopLevel("listSources")
  def listSources(contentType: String) = {
    dom.window.console.log("listSources " + contentType)
    PipelinesXhr.listSources(Option(contentType).filterNot(_.isEmpty)).onComplete {
      case Success(response) =>
        dom.window.console.log(response.toString())
      case Failure(err) =>
        dom.window.alert(err.toString)
    }
  }

  @JSExportTopLevel("listTypes")
  def listTypes() = {
    PipelinesXhr.listAllTypes().onComplete {
      case Success(response) =>
        dom.window.console.log(response.toString())
      case Failure(err) =>
        dom.window.alert(err.toString)
    }
  }

  @JSExportTopLevel("valueOf")
  def valueOf(id: String): String = {
    valueOf(id, document.getElementById(id))
  }

  @JSExportTopLevel("loginOnKeyUp")
  def loginOnKeyUp(userInputId: String, pwdInputId: String, e: KeyboardEvent) = {
    if (e.keyCode == 13) {
      login(userInputId, pwdInputId)
    }
  }

  @JSExportTopLevel("login")
  def login(userInputId: String, pwdInputId: String) = {

    val user    = valueOf(userInputId)
    val pwd     = valueOf(pwdInputId)
    val request = LoginRequest(user, pwd)

    val redirectUrl = dom.window.document.location.search match {
      case ParseRedirect(url) => url
      case _                  => ""
    }

    dom.window.console.log("search:" + dom.window.document.location.search + ", redirectUrl is " + redirectUrl)

    val loginResponseFuture: Future[LoginResponse] = PipelinesXhr.userLogin.loginEndpoint.apply(request, Option(redirectUrl).filterNot(_.isEmpty))
    loginResponseFuture.onComplete {
      case Success(response) => PipelinesXhr.onLogin(response)
      case Failure(err) =>
        dom.window.alert(err.toString)
    }
  }

  @JSExportTopLevel("renderQuery")
  def renderQuery(queryButtonId: String, resultsDivId: String): Node = {

    import scalatags.JsDom.all._

    val results = dom.window.document.getElementById(resultsDivId)

    val queryButton = button("Query", `class` := "btn").render
    queryButton.onclick = (e: dom.MouseEvent) => {
      e.stopPropagation
//      query(
//        clientElementElementId = "clientId",
//        groupElementId = "groupId",
//        topicElementId = "topic",
//        filterTextElementId = "query",
//        filterExpressionIncludeMatchesId = "filterExpressionIncludeMatchesId",
//        offsetElementId = "offset",
//        rateLimitElementId = "rate",
//        strategyElementId = "strategy",
//        isBinaryStreamId = "isBinaryStreamId"
//      ) { msg =>
//        results.appendChild(div(span(msg.data.toString), br()).render)
//      }
    }

    val clearButton = button("Clear", `class` := "btn").render
    clearButton.onclick = (e: dom.MouseEvent) => {
      e.stopPropagation

      results.innerHTML = ""
    }

    dom.window.document.getElementById(queryButtonId).appendChild(div(queryButton, br(), clearButton).render)
  }

}
