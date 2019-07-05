package pipelines.client.users

import org.scalajs.dom.html.{Anchor, Div}
import pipelines.client.Constants
import pipelines.client.layout.GoldenLayout
import pipelines.client.menu.Menu.css
import pipelines.users.UserStatusResponse
import scalatags.JsDom.all.{`class`, a, div, href, style, _}

object UsersMenu {

//  addMenuDropDown(s"User '${status.userName}'", status.userId, myLayout, true)

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
