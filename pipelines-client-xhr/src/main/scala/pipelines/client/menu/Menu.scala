package pipelines.client.menu

import io.circe.{Decoder, Json, ObjectEncoder}
import org.scalajs.dom.html.{Button, Div, LI}
import org.scalajs.dom.raw._
import pipelines.client.HtmlUtils
import pipelines.client.layout.GoldenLayout
import pipelines.client.source.SourceMenu
import pipelines.client.users.UsersMenu
import scalatags.JsDom.all._

import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

/**
  */
@JSExportTopLevel("Menu")
object Menu {

  import HtmlUtils._

  /**
    * The styles declared in menu.css
    */
  object css {
    def inlineStyle(rightAligned: Boolean) = if (rightAligned) "float:right;" else "float:left;"
    def pos(rightAligned: Boolean)         = if (rightAligned) "" else "left:0;"
    val DropDown                           = "dropdown"
    val MenuButton                         = "dropbtn"
    val MenuContainer                      = "menuContainer"
    val DropDownContent                    = "dropdown-content"

  }

  /** the domain-specific configuration which is accessible to the layout components
    *
    * @param title
    * @param componentName
    * @param `type`
    * @param componentState
    */
  case class ItemConfig(title: String, componentName: String, `type`: String = "component", componentState: Json = Json.Null)
  object ItemConfig {
    implicit val encoder: ObjectEncoder[ItemConfig] = io.circe.generic.semiauto.deriveEncoder[ItemConfig]
    implicit val decoder: Decoder[ItemConfig]       = io.circe.generic.semiauto.deriveDecoder[ItemConfig]

    def apply(title: String, componentName: String, state: Json): js.Dynamic = {
      import io.circe.syntax._

      val conf = ItemConfig(title, componentName = componentName, componentState = state)
      JSON.parse(conf.asJson.noSpaces)
    }
  }

  def dropDownMenu(title: String, rightAligned: Boolean)(content: Element): Div = {
    //i(style := "padding-left:10px;", "V")
    val btn: Button = button(`class` := css.MenuButton, title).render
    div(`class` := css.DropDown, style := css.inlineStyle(rightAligned))(
      btn,
      content
    ).render
  }

  def menuContainer = elmById(css.MenuContainer)


  @JSExport
  def initialise(myLayout: GoldenLayout) = {
    SourceMenu.init(myLayout)
    UsersMenu.init(myLayout)
  }

  def addMenuItem(title: String)(itemOp: MouseEvent => Unit): LI = {
    val newLi = li(`class` := css.MenuButton, title).render

    newLi.onclick = (e: MouseEvent) => {
      e.stopPropagation()
      itemOp(e)
    }

    menuContainer.appendChild(newLi)
    newLi
  }

}
