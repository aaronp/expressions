package pipelines.client.source

import pipelines.client.tables.Clusterize
import pipelines.client.{ClientSocketState, HtmlUtils, PipelinesXhr}
import pipelines.rest.socket.{AddressedBinaryMessage, AddressedTextMessage}
import scalatags.Text.all.{div, _}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

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

    val socketFuture: Future[ClientSocketState] = PipelinesXhr.createSocket()

    import PipelinesXhr.implicits._
    socketFuture.foreach { socket: ClientSocketState =>
      socket.subscribeToSourceEvents()
      socket.subscribeToSinkEvents()

      val inst = {
        HtmlUtils.log(s"Creating clusterize with $socket")
        Clusterize(config)
      }

      socket.messages.foreach {
        case AddressedBinaryMessage(to, data) =>
          inst.append(Seq(s"<tr><td>to:${to}</td><td>${data.size} bytes</td></tr>"))
        case AddressedTextMessage(to, msg) =>
          inst.append(Seq(s"<tr><td>to:${to}</td><td>text:${msg}</td></tr>"))
      }(socket.scheduler)
    }

    divText
  }

}
