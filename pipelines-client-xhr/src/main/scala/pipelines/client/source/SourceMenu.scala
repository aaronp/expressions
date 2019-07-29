package pipelines.client.source

import org.scalajs.dom.html.{Anchor, Div}
import org.scalajs.dom.raw.MouseEvent
import pipelines.client.layout.{GoldenLayout, GoldenLayoutComponents}
import pipelines.client.menu.Menu.{addMenuItem, css, dropDownMenu, menuContainer}
import pipelines.client.menu.Modals
import pipelines.client.source.NewPushSourceModal.FormData
import pipelines.client.{HtmlUtils, PipelinesXhr}
import pipelines.reactive.repo.{CreatedPushSourceResponse, ListRepoSourcesResponse, ListedDataSource, PushedValueResponse}
import scalatags.JsDom.all.{`class`, a, div, href, style, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object SourceMenu {
  def init(myLayout: GoldenLayout): Unit = {
    def appendSourcesMenu(sources: Seq[ListedDataSource]): Unit = {
      menuContainer.appendChild(dropDownMenu("Sources", false) {
        SourceMenu(sources, myLayout)
      })

      val initialState = SourceTableState()
      // allow the 'Sources' menu item to clicked
      val sourceTableLI = addMenuItem("Sources") { _ =>
        GoldenLayoutComponents.addComponent(myLayout, initialState.asLayoutItemConfig())
      }

      // allow the 'Sources' menu item to be dragged onto the table
      myLayout.createDragSource(sourceTableLI, initialState.asLayoutItemConfig())
    }

    PipelinesXhr.listSources(Map.empty).onComplete {
      case Success(ListRepoSourcesResponse(sources)) =>
        appendSourcesMenu(sources)
      case Failure(err) =>
        HtmlUtils.raiseError(s"Error listing sources: $err")
        throw err
    }
  }

  def apply(sources: Seq[ListedDataSource], layout: GoldenLayout): Div = {

    val newPush: Anchor = {
      val newSourceLink = a(href := "#", "New Push Source").render

      newSourceLink.onclick = (_: MouseEvent) => {
        Modals.pushSource.showPushModal { fd: FormData =>
          val future = PipelinesXhr.createSource(fd.name, fd.persist, fd.metadata)

          future.onComplete {
            case Success(CreatedPushSourceResponse(name, contentType, metadata)) =>
              HtmlUtils.log(s"Created $name of type $contentType w/ $metadata")

            case Success(PushedValueResponse(ok)) =>
              HtmlUtils.raiseError(s"Expected to have created a new source, but we somehow pushed a value to '${fd.name}'")
            case Failure(err) =>
              HtmlUtils.raiseError(s"Error creating source '$name': $err")
          }
        }
      }

      newSourceLink
    }

    val sourceLinks: Seq[Anchor] = sources.map { source =>
      val addLink = a(href := "#", source.description).render
      addLink.onclick = _ => {
        val newConfig = PushSourceState(source).asLayoutItemConfig()
        GoldenLayoutComponents.addDragSourceChild(layout, addLink, newConfig)
      }

      addLink
    }

    val newWebSocket: Anchor = a(href := "#", "List Sources").render
    newWebSocket.onclick = _ => {
      val newConfig = SourceTableState().asLayoutItemConfig()
      GoldenLayoutComponents.addDragSourceChild(layout, newWebSocket, newConfig)
    }

    val links: Seq[Anchor] = newPush +: newWebSocket +: sourceLinks

    val subMenu = div(`class` := css.DropDownContent, style := css.pos(false)).render
    links.foreach { link =>
      subMenu.appendChild(link)
    }
    subMenu
  }
}
