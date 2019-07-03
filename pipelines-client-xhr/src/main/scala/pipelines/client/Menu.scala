package pipelines.client

import io.circe.{Decoder, Json, ObjectEncoder}
import org.scalajs.dom.html.LI
import pipelines.client.layout.GoldenLayout
import scalatags.JsDom
import scalatags.JsDom.all._

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/**
  * <button >Dropdown</button>
  * <div id="menuContainer" class="dropdown-content">
  * <a href="#">New Source</a>
  * <a href="#">Source 1</a>
  * <a href="#">Source 2</a>
  * </div>
  */
@JSExportTopLevel("Menu")
object Menu {
  case class ItemConfig(title: String, componentName: String, `type`: String = "component", componentState: Json = Json.Null)
  object ItemConfig {
    implicit val encoder: ObjectEncoder[ItemConfig] = io.circe.generic.semiauto.deriveEncoder[ItemConfig]
    implicit val decoder: Decoder[ItemConfig]       = io.circe.generic.semiauto.deriveDecoder[ItemConfig]

  }
  import HtmlUtils._
  @JSExport
  def newMenuItemConfig(title: String, text: String) = {
    val conf = ItemConfig(title, componentName = "testComponent", componentState = Json.obj("text" -> Json.fromString(text)))
    import io.circe.syntax._
    conf.asJson.noSpaces
  }
  @JSExport
  def addMenuItem(title: String, text: String, layout: GoldenLayout) = {
    val newLi         = li(title).render
    val menuContainer = elmById("menuContainer")
    menuContainer.appendChild(newLi)
    val c1 = newMenuItemConfig(title, text)

    layout.createDragSource(newLi, JSON.parse(c1))
  }
}
