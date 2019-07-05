package pipelines.client.source

import pipelines.client.HtmlUtils
import scalatags.Text.all.{div, h2, p, _}

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("PushSourceComponent")
object PushSourceComponent {

  @JSExport
  def render(st8: js.Dynamic): String = {
    val json: String = JSON.stringify(st8)

    val pss = io.circe.parser.decode[PushSourceState](json)
    pss match {
      case Left(err) =>
        HtmlUtils.raiseError(s"Couldn't parse $json: $err")
        throw err
      case Right(state) => PushSourceComponent(state)
    }
  }

  def apply(st8: PushSourceState): String = {
    import io.circe.syntax._

    div(`class` := "pipeline-component")(
      h1("Push Source"),
      h2(st8.source.createdBy()),
      h3(st8.source.id()),
      p(st8.asJson.spaces2)
    ).render
  }

}
