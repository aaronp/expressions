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
