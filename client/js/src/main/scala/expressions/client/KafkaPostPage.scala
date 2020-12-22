package expressions.client

import org.scalajs.dom.document
import org.scalajs.dom.html.Div

import scala.scalajs.js.annotation.JSExportTopLevel
import scalatags.JsDom.all._
@JSExportTopLevel("KafkaPostPage")
case class KafkaPostPage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val configTextArea    = textarea(cols := 140, rows := 20).render
  val recordTextArea    = textarea(cols := 140, rows := 20).render
  val configNameTextBox = input(`type` := "text", id := "config-text", size := 120, value := "application.conf").render

  val postForm = div(
    div(
      div(
        h2("Kafka:"),
        label(`for` := configNameTextBox.id)("Config:"),
        div(configNameTextBox),
//        div(saveConfigButton, reloadConfigButton),
        configTextArea
      ),
      h2("Record:"),
//      div(jsonInputTextArea),
//      h2("Result:"),
//      resultTextArea,
//      div(clearButton)
    )
  ).render

  targetDiv.innerHTML = ""
  targetDiv.appendChild(postForm)
}
