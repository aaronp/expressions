package expressions.client

import org.scalajs.dom.document
import org.scalajs.dom.html.Div

import scala.scalajs.js.annotation.JSExportTopLevel


@JSExportTopLevel("CachePage")
case class CachePage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]
}
