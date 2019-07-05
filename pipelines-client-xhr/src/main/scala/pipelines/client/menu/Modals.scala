package pipelines.client.menu

import org.scalajs.dom.html.Div
import pipelines.client.HtmlUtils
import pipelines.client.HtmlUtils.divById
import pipelines.client.source.NewPushSourceModal

object Modals {

  def modalsContainer: Div = divById("modalsContainer")

  object pushSource {
    def showPushModal(callback: NewPushSourceModal.FormData => Unit): Div = {
      NewPushSourceModal.render(modalsContainer)(callback)
    }
  }

}
