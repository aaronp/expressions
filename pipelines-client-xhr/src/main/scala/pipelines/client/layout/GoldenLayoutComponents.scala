package pipelines.client.layout

import org.scalajs.dom.Node
import pipelines.client.source.PushSourceState
import pipelines.client.{Constants, HtmlUtils}
import pipelines.web.GoldenLayoutConfig

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

/**
  * Contains the functions for dealing w/ our layout
  */
object GoldenLayoutComponents extends HtmlUtils {

  /**
    * Adds the component w/ the given 'newConfig' either to the currently selected component or just appends it to the
    * layout.
    *
    * TODO: append it as a row or column of the biggest area, not just blindly
    *
    * @param layout
    * @param newConfig
    */
  def addComponent(layout: GoldenLayout, newConfig: js.Dynamic): Unit = {
    val selectedOpt = Option(layout.selectedItem).filterNot(_ == null)
    selectedOpt match {
      case None           => GoldenLayoutComponents.addChild(newConfig)
      case Some(selected) => selected.addChild(newConfig)
    }
  }

  def addDragSourceChild(layout: GoldenLayout, node: Node, newConfig: js.Dynamic): Unit = {
    log(s"Adding drag source $newConfig")
    layout.createDragSource(node, newConfig)

    addComponent(layout, newConfig)
  }

  /**
    * This exposes a callback into our manual 'addLayoutChild' in app.js
    *
    * @param newItemConfig
    */
  def addChild(newItemConfig: js.Dynamic): Unit = {
    if (newItemConfig != null) {
      js.Dynamic.global.addLayoutChild(newItemConfig)
    }
  }

  @JSExportTopLevel("initialGoldenLayout")
  def initialLayout(): String = {
    val push: GoldenLayoutConfig = {
      import io.circe.syntax._
      val pushState = PushSourceState().asJson
      GoldenLayoutConfig.component(Constants.components.pushSource, pushState)
    }

    val layout = GoldenLayoutConfig.row() :+ push

    layout.toConfigJson().spaces2
  }

}
