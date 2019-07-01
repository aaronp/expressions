package pipelines.client.layout

import io.circe.Json
import pipelines.client.HtmlUtils
import pipelines.web.GoldenLayoutConfig
import scalatags.Text.all._

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSExportTopLevel

/**
  * Contains the functions for dealing w/ our layout
  */
object GoldenLayoutComponents extends HtmlUtils {

  @JSExportTopLevel("initialGoldenLayout")
  def initialLayout() = {
    def test(name: String, label: String): GoldenLayoutConfig = GoldenLayoutConfig.component(name, Json.obj("label" -> Json.fromString(label)))

    val col    = GoldenLayoutConfig.column() :+ test("testComponent", "B") :+ test("testComponent2", "C")
    val layout = GoldenLayoutConfig.row() :+ test("testComponent", "A") :+ col

    log(layout.toConfigJson.spaces2)

    layout.toConfigJson.spaces2
  }

  @JSExportTopLevel("renderExample")
  def renderExample(st8: js.Dynamic): String = {
    val name         = st8.componentName
    val json: String = JSON.stringify(st8)

    div(
      h1(id := "title", st8.label.toString),
      p(json)
    ).render

  }
  @JSExportTopLevel("renderExample2")
  def renderExample2(st8: js.Dynamic): String = {
    val name         = st8.componentName
    val json: String = JSON.stringify(st8)

    div(
      h2(id := "title", st8.label.toString),
      p("Second component:"),
      p(json)
    ).render

  }
}
