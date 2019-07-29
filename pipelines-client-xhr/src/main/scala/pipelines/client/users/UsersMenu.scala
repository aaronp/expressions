package pipelines.client.users

import org.scalajs.dom.html.{Anchor, Div}
import pipelines.client.HtmlUtils.redirectTo
import pipelines.client.layout.GoldenLayout
import pipelines.client.menu.Menu
import pipelines.client.menu.Menu.{addMenuItem, css, menuContainer}
import pipelines.client.{Constants, PipelinesXhr}
import pipelines.users.UserStatusResponse
import scalatags.JsDom.all.{`class`, a, div, href, style, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object UsersMenu {

  def init(myLayout: GoldenLayout): Unit = {

    PipelinesXhr.userStatus.statusEndpoint.apply().onComplete {
      case Failure(_) =>
        addMenuItem("login") { _ =>
          redirectTo(Constants.pages.LoginPage)
        }
      case Success(status) =>
        menuContainer.appendChild(Menu.dropDownMenu(status.userName, true) {
          UsersMenu(status, myLayout)
        })
    }
  }

  def apply(status: UserStatusResponse, layout: GoldenLayout): Div = {

    import pipelines.client.HtmlUtils._

    val logout: Anchor = {
      val link = a(href := "#", "Logout").render

      link.onclick = (e) => {
        UserFunctions.logout()
        redirectTo(Constants.pages.LoginPage)
      }

      link
    }

    div(`class` := css.DropDownContent, style := css.pos(true))(
      logout
    ).render

  }
}
