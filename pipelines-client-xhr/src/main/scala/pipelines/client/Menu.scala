package pipelines.client

import io.circe.{Decoder, Json, ObjectEncoder}
import org.scalajs.dom
import org.scalajs.dom.html.{Button, Div}
import org.scalajs.dom.raw._
import pipelines.client.layout.GoldenLayout
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.{Failure, Success}

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

  }

  @JSExport
  def newMenuItemConfig(title: String, name: String, state: Json) = {
    val conf = ItemConfig(title, componentName = name, componentState = state)
    import io.circe.syntax._

    JSON.parse(conf.asJson.noSpaces)
  }

  @JSExport
  def initialise(myLayout: GoldenLayout) = {
    PipelinesXhr.userStatus.statusEndpoint.apply().onComplete {
      case Failure(_) =>
        addMenuItem("login", "please log in", myLayout) { e =>
          log(s"login click: $e")
          redirectTo(PageNames.LoginPage)
        }
      case Success(status) =>
        log(s"User status is $status")
        addMenuDropDown(s"User '${status.userName}'", status.userId, myLayout, true)
    }

    addMenuItem("Add me!", "You've added me!", myLayout) { e =>
      log(s"add me click: $e")
    }
    addMenuItem("Me too!", "You've added me too!", myLayout) { e =>
      log(s"me too click: $e")
    }
    addMenuDropDown("Sources", "...please", myLayout)

    PipelinesApp.listSources().onComplete {
      case Success(value) =>
        log(s"got sources: $value")
      case Failure(err) =>
        log(s"error fetching sources: $err")
    }
  }

  @JSExport
  def addMenuItem(title: String, text: String, layout: GoldenLayout)(itemOp: MouseEvent => Unit) = {
    val newLi = li(`class` := css.MenuButton, title).render

    newLi.onclick = (e: MouseEvent) => {
      e.stopPropagation()
      itemOp(e)
    }

    val menuContainer = elmById(css.MenuContainer)

    menuContainer.appendChild(newLi)
    val c1 = newMenuItemConfig(title, "testComponent", Json.obj("text" -> Json.fromString(text)))

    layout.createDragSource(newLi, c1)
  }

  /**
    * Thanks https://www.w3schools.com/howto/howto_js_dropdown.asp
    *
    * @param title
    * @param text
    * @param layout
    * @return
    */
  def addMenuDropDown(title: String, text: String, layout: GoldenLayout, rightAligned: Boolean = false): Node = {

    val newButton = {

      val links = (1 to 3).map { i =>
        val linkText = s"Link $i"
        a(href := "#", linkText)
      }

      val subMenu: Div = div(`class` := css.DropDownContent, style := css.pos(rightAligned))(links: _*).render
      (0 until subMenu.childNodes.length).foreach { i =>
        val child = subMenu.childNodes(i)

        child match {
          case anchor: HTMLAnchorElement =>
            def newConfig = newMenuItemConfig(anchor.text, "testComponent", Json.obj("text" -> Json.fromString(text)))

            anchor.onclick = (e) => {
              Option(layout.selectedItem) match {
                case None           => PipelinesApp.addChild(newConfig)
                case Some(selected) => selected.addChild(newConfig)
              }
            }
          case _ =>
        }
        val c1 = newMenuItemConfig(child.textContent, "testComponent", Json.obj("text" -> Json.fromString(text)))
        layout.createDragSource(child, c1)
      }

      val btn: Button = button(`class` := css.MenuButton, title, i(style := "padding-left:10px;", "V")).render

      div(`class` := css.DropDown, style := css.inlineStyle(rightAligned))(
        btn,
        subMenu
      ).render
    }

    elmById(css.MenuContainer).appendChild(newButton)
  }
}
