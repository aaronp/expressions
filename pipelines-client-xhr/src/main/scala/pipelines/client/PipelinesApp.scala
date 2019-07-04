package pipelines.client

import org.scalajs.dom
import org.scalajs.dom.raw.Node
import pipelines.reactive.repo.ListRepoSourcesResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

/**
  */
object PipelinesApp extends HtmlUtils {

  @JSExportTopLevel("listSources")
  def listSources(queryParams: Map[String, String] = Map.empty): Future[ListRepoSourcesResponse] = {
    PipelinesXhr.listSources(queryParams).map { response =>
      dom.window.console.log(response.toString())
      response
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
    }

    val clearButton = button("Clear", `class` := "btn").render
    clearButton.onclick = (e: dom.MouseEvent) => {
      e.stopPropagation

      results.innerHTML = ""
    }

    dom.window.document.getElementById(queryButtonId).appendChild(div(queryButton, br(), clearButton).render)
  }

}
