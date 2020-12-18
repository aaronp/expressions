package expressions.client

import expressions.client.kafka.{PostRecord, StartedConsumer}
import io.circe.Json
import io.circe.parser.parse
import org.scalajs.dom.html.Div
import org.scalajs.dom.{document, window}
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.Date
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success, Try}

@JSExportTopLevel("RunningPage")
case class RunningPage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val keyTextArea = textarea(cols := 140, rows := 4).render
  keyTextArea.value = """key{{i}}""".stripMargin

  val recordTextArea = textarea(cols := 140, rows := 20).render
  recordTextArea.value = """{
                           |  "data{{i}}" : "value-{{i}}",
                           |  "flag" :  true
                           |}""".stripMargin

  val configTextArea    = textarea(cols := 140, rows := 10).render
  val repeatText        = input(`type` := "text", value := "1").render
  val topicOverrideText = input(`type` := "text", value := "").render
  val partitionText     = input(`type` := "text", value := "").render
  val tableDiv          = div().render
  val configDiv         = div().render

  val refreshButton = button("Refresh").render
  refreshButton.onclick = e => {
    e.preventDefault()
    refresh()
  }

  def refresh(): Unit = {
    Client.kafka.running().foreach { running =>
      tableDiv.innerHTML = ""
      tableDiv.appendChild(RunningTable(running).render)
    }
  }

  case class RunningTable(running: List[StartedConsumer]) {
    val rows = running.map {
      case StartedConsumer(id, cfg, started) =>
        val stopButton = a(href := "#")("stop").render
        stopButton.onclick = e => {
          e.preventDefault()
          Client.kafka.stop(id).foreach {
            case true  => refresh()
            case false => refresh()
          }
        }
        val idButton = a(href := "#")(id).render
        idButton.onclick = e => {
          e.preventDefault()
          configDiv.innerHTML = cfg
        }
        tr(td(stopButton), td(idButton), td(s"${new Date(started.toDouble)}"))
    }
    val header = tr(
      th(""),
      th("ID"),
      th("Started")
    )
    val render = table((header +: rows): _*).render
  }

  val postForm = div(
    div(
      div(
        h2("Consumers:"),
        tableDiv,
        div(refreshButton),
        configDiv
      )
    )
  ).render

  targetDiv.innerHTML = ""
  targetDiv.appendChild(postForm)
  refresh()
}
