package expressions.client

import org.scalajs.dom.document
import org.scalajs.dom.html.Div
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}
@JSExportTopLevel("CachePage")
case class CachePage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val pathText       = input(`class` := "input-field", `type` := "text", value := "path/to/data").render
  val cacheCheckbox  = input(`class` := "input-field", `type` := "checkbox", value := "false").render
  val recordTextArea = textarea(cols := 140, rows := 20).render
  val message        = div().render

  val refreshButton = button("Refresh").render
  refreshButton.onclick = e => {
    e.preventDefault()
    refresh()
  }

  val saveButton = button("Save").render
  saveButton.onclick = e => {
    e.preventDefault()
    saveData()
  }

  val postForm = div(
    div(`class` := "form-style")(
      div()(
        h2("Store Data:"),
        div(
          label(`for` := cacheCheckbox.id)(span("Cache"), span(cacheCheckbox))
        ),
        div(
          label(`for` := cacheCheckbox.id)(span("Path"), span(pathText))
        ),
        div(
          div(label(`for` := recordTextArea.id)(span("Data"))),
          div(recordTextArea)
        ),
        div(saveButton, refreshButton),
        message
      )
    )
  ).render

  targetDiv.innerHTML = ""
  targetDiv.appendChild(postForm)

  def saveData(cached: Boolean = cacheCheckbox.checked) = {
    val future = if (cached) {
      Client.cache.storeAt(pathText.value, recordTextArea.value)
    } else {
      Client.disk.storeAt(pathText.value, recordTextArea.value)
    }

    future.onComplete {
      case Failure(err) =>
        message.innerHTML = s"Error: $err"
      case Success(true) =>
        message.innerHTML = "Created"
      case Success(false) =>
        message.innerHTML = "Updated"
    }
  }
  def path = pathText.value.split(",", -1).toList
  def refresh(cached: Boolean = cacheCheckbox.checked) = {
    val future: Future[Option[String]] = if (cached) {
      Client.cache.read(path).map(_.map(_.spaces4))
    } else {
      Client.disk.read(path)
    }

    future.onComplete {
      case Failure(err) =>
        message.innerHTML = s"Error: $err"
      case Success(Some(data)) =>
        message.innerHTML = ""
        recordTextArea.value = data
      case Success(None) =>
        message.innerHTML = "not found"
    }
  }
}
