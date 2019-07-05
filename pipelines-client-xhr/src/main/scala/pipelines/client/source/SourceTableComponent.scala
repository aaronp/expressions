package pipelines.client.source

import pipelines.client.tables.Clusterize
import pipelines.client.{HtmlUtils, PipelinesXhr}
import scalatags.Text.all.{div, _}

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

    PipelinesXhr
      .createSocket()
      .foreach { socket =>
        import socket._
        val inst = {
          HtmlUtils.log("Creating clusterize")
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
      }(PipelinesXhr.execContext)

    divText
  }

}
