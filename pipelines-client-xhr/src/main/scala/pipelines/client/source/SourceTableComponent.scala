package pipelines.client.source

import pipelines.client.tables.Clusterize
import pipelines.client.{ClientSocketState, HtmlUtils, PipelinesXhr}
import pipelines.reactive.repo.ListRepoSourcesResponse
import pipelines.reactive.tags
import scalatags.Text.all.{div, _}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.control.NonFatal

@JSExportTopLevel("SourceTableComponent")
object SourceTableComponent {

  @JSExport
  def render(st8: js.Dynamic): String = {
    val json: String = JSON.stringify(st8)

    val pss = io.circe.parser.decode[SourceTableState](json)
    pss match {
      case Left(err) =>
        HtmlUtils.raiseError(s"Couldn't parse $json: $err")
        throw err
      case Right(state) => apply(state)
    }
  }

  def apply(st8: SourceTableState): String = {

    HtmlUtils.log("Rendering " + st8)
    val config = st8.tableConfig

    val divText = div(`class` := "pipeline-component", st8.name)(
      div(`class` := "clusterize")(
        table(
          thead(
            tr(
              th("Headers")
            )
          )
        ),
        div(id := config.scrollId, `class` := "clusterize-scroll")(
          table(
            tbody(id := config.contentId, `class` := "clusterize-content")(
              tr(`class` := "clusterize-no-data")(
                td("Loading data...")
              )
            )
          )
        )
      )
    ).render

    val sourceList: Future[ListRepoSourcesResponse] = PipelinesXhr.listSources(Map.empty)
    val socketFuture: Future[ClientSocketState]     = PipelinesXhr.createSocket()

    import PipelinesXhr.implicits._
    for {
      sources <- sourceList
      socket  <- socketFuture
    } {
      socket.subscribe(Map(tags.Label -> tags.typeValues.Push))

      import socket._

      sources.sources.foreach { src =>
        HtmlUtils.log(s"SOURCE: $src")
      }
      val inst = {
        HtmlUtils.log(s"Creating clusterize w/ ${sources.sources.size} sources")
        Clusterize(config)
      }
      socket.messages.foreach { msg =>
        try {
          inst.append(Seq(s"<tr><td>${msg}</td></tr>"))
        } catch {
          case NonFatal(e) =>
            inst.append(Seq(s"<tr><td>error: ${e}</td></tr>"))
        }
      }
    }

    divText
  }

}
