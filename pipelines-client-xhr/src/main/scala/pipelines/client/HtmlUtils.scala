package pipelines.client

import java.util.UUID

import org.scalajs.dom
import org.scalajs.dom.raw._
import org.scalajs.dom.{document, html, window}

import scala.collection.immutable
import scala.util.control.NonFatal

object HtmlUtils extends HtmlUtils

trait HtmlUtils {

  def elmById(id: String) = document.getElementById(id)

  def childrenFor(html: HTMLElement): immutable.IndexedSeq[Node] = {
    (0 until html.childNodes.length).map { i =>
      html.childNodes.item(i)
    }
  }

  /** @param messagesId the id for the element where the message should be set
    * @param message the message to set
    */
  def setMessage(messagesId: String, message: String) = {
    elmById(messagesId) match {
      case x: HTMLTextAreaElement => x.value = message
      case x: HTMLInputElement    => x.value = message
      case other                  => sys.error(s"Element '$messagesId' is $other so can't set $message")
    }
  }

  /**
    * Thanks SO!
    * https://stackoverflow.com/questions/503093/how-do-i-redirect-to-another-webpage
    *
    * @param page the page the URL to go to
    */
  def redirectTo(page: String) = {
    log(s"""
         |window.location.hostname=${window.location.hostname}
         |window.location.host=${window.location.host}
         |window.location.pathname=${window.location.pathname}
         |window.location.search=${window.location.search}
         |window.location.href=${window.location.href}
       """.stripMargin)

    window.location.replace(page)
  }

  /**
    * Thanks SO!
    * https://stackoverflow.com/questions/503093/how-do-i-redirect-to-another-webpage
    *
    * @param page the page the URL to go to
    */
  def gotoLink(page: String) = {
    window.location.href = page
  }

  def showAlert(text: String): Unit = {
    dom.window.alert(text)
  }
  def log(text: String): Unit = {
    dom.window.console.log(text)
  }

  def valueOfNonEmpty(id: String, uniqueId: => String = UUID.randomUUID.toString): String = {
    document.getElementById(id) match {
      case x: HTMLTextAreaElement =>
        Option(x.value).map(_.trim).filterNot(_.isEmpty).getOrElse {
          val default = uniqueId
          x.value = default
          default
        }
      case x: HTMLInputElement =>
        Option(x.value).map(_.trim).filterNot(_.isEmpty).getOrElse {
          val default = uniqueId
          x.value = default
          default
        }
      case other =>
        sys.error(s"valueOf('$id') was ${other}")
        uniqueId
    }
  }

  def valueOf(id: String, elm: Element = null): String = {
    try {
      val result = Option(elm).getOrElse(document.getElementById(id)) match {
        case x: HTMLTextAreaElement => x.value
        case x: HTMLInputElement    => x.value
        case other =>
          sys.error(s"valueOf('$id') was ${other}")
      }
      result
    } catch {
      case NonFatal(err) =>
        dom.window.console.log(s"Couldn't get value for '$id': $err")
        ""
    }
  }

  def appendPar(targetNode: dom.Node, text: String): Unit = {
    val parNode  = dom.document.createElement("p")
    val textNode = document.createTextNode(text)
    parNode.appendChild(textNode)
    targetNode.appendChild(parNode)
  }

  def mouseMove(pre: html.Pre) = {
    pre.onmousemove = { (e: dom.MouseEvent) =>
      pre.textContent = s"""e.clientX ${e.clientX}
                           |e.clientY ${e.clientY}
                           |e.pageX   ${e.pageX}
                           |e.pageY   ${e.pageY}
                           |e.screenX ${e.screenX}
                           |e.screenY ${e.screenY}
         """.stripMargin
    }
  }

  def base64EncodeInputToDiv(in: html.Input, out: html.Div) = {
    in.onkeyup = { (e: dom.Event) =>
      out.textContent = window.btoa(in.value)
    }
  }
}
