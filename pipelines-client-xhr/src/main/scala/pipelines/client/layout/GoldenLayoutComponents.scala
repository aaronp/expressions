package pipelines.client.layout

import io.circe.Json
import pipelines.client.HtmlUtils
import pipelines.client.source.{PushSourceComponent, PushSourceState}
import pipelines.web.GoldenLayoutConfig
import scalatags.Text.all._

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.JSExportTopLevel

/**
  * Contains the functions for dealing w/ our layout
  */
object GoldenLayoutComponents extends HtmlUtils {

  def addChild(newItemConfig: js.Dynamic) = {
    if (newItemConfig != null) {
      js.Dynamic.global.addLayoutChild(newItemConfig)
    }
  }

  @JSExportTopLevel("initialGoldenLayout")
  def initialLayout() = {
    def test(name: String, label: String): GoldenLayoutConfig = GoldenLayoutConfig.component(name, Json.obj("label" -> Json.fromString(label)))

    val col = GoldenLayoutConfig.column() :+ test("testComponent", "B") :+ test("testComponent2", "C")

    val push = {
      import io.circe.syntax._
      val pushState = PushSourceState("default", "base", "pushy mc push-face", false, "pushSource").asJson
      GoldenLayoutConfig.component("pushSource", pushState)
    }

    val layout = GoldenLayoutConfig.row() :+ push :+ col

    layout.toConfigJson().spaces2
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

  @JSExportTopLevel("renderPushSource")
  def renderPushSource(st8: js.Dynamic): String = {
//    val name         = st8.componentName
    val json: String = JSON.stringify(st8)
    import io.circe.syntax._
    val pss = io.circe.parser.decode[PushSourceState](json)

    log(s"pss json is ${pss}")
    pss match {
      case Left(err) =>
        log(s"Couldn't parse '$json' as PushSourceState: $err")
        PushSourceComponent.render(PushSourceState("a", "b", "c", true, "pushSource"))

      case Right(state) =>
        PushSourceComponent.render(state)
    }

  }
}
