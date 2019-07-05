package pipelines.client.menu

import io.circe.{Decoder, Json, ObjectEncoder}
import org.scalajs.dom.html.{Button, Div, LI}
import org.scalajs.dom.raw._
import pipelines.client.layout.{GoldenLayout, GoldenLayoutComponents}
import pipelines.client.source.{SourceMenu, SourceTableState}
import pipelines.client.users.UsersMenu
import pipelines.client.{Constants, HtmlUtils, PipelinesXhr}
import pipelines.reactive.repo.{ListRepoSourcesResponse, ListedDataSource}
import pipelines.reactive.tags
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSON
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}
import scala.util.{Failure, Success}

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

  private def dropDownMenu(title: String, rightAligned: Boolean)(content: Element): Div = {
    val btn: Button = button(`class` := css.MenuButton, title, i(style := "padding-left:10px;", "V")).render
    div(`class` := css.DropDown, style := css.inlineStyle(rightAligned))(
      btn,
      content
    ).render
  }

  def menuContainer = elmById(css.MenuContainer)

  @JSExport
  def initialise(myLayout: GoldenLayout) = {

    def appendSourcesMenu(sources: Seq[ListedDataSource]): Unit = {
      menuContainer.appendChild(dropDownMenu("Sources", false) {
        SourceMenu(sources, myLayout)
      })

      locally {

        def newRow(item: ListedDataSource) = {
          tr(
            td(item.name())
          ).render.outerHTML
        }

        val rows         = sources.map(newRow) :+ newRow(ListedDataSource(Map(tags.Name -> "fake"), None))
        val initialState = SourceTableState(content = rows)
        // allow the 'Sources' menu item to clicked
        val sourceTableLI = addMenuItem("Sources") { _ =>
          GoldenLayoutComponents.addComponent(myLayout, initialState.asLayoutItemConfig())
        }

        // allow the 'Sources' menu item to be dragged onto the table
        myLayout.createDragSource(sourceTableLI, initialState.asLayoutItemConfig())
      }
    }

    PipelinesXhr.listSources(Map.empty).onComplete {
      case Success(ListRepoSourcesResponse(sources)) =>
        appendSourcesMenu(sources)
      case Failure(err) =>
        HtmlUtils.raiseError(s"Error listing sources: $err")
        throw err
    }

    PipelinesXhr.userStatus.statusEndpoint.apply().onComplete {
      case Failure(_) =>
        addMenuItem("login") { _ =>
          redirectTo(Constants.pages.LoginPage)
        }
      case Success(status) =>
        menuContainer.appendChild(dropDownMenu(status.userName, true) {
          UsersMenu(status, myLayout)
        })
    }
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
