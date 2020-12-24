package expressions.client

import expressions.client.kafka.{ConsumerStats, StartedConsumer}
import io.circe.syntax.EncoderOps
import org.scalajs.dom.document
import org.scalajs.dom.html.Div
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.Date
import scala.scalajs.js.annotation.JSExportTopLevel

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

  val tableDiv  = div().render
  val configDiv = div().render
  val aboutDiv  = div().render

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
          Client.kafka.stats(id).foreach {
            case None =>
              aboutDiv.innerHTML = "WTF?"

            case Some(stats: ConsumerStats) =>
              aboutDiv.innerHTML = stats.asJson.spaces2

          }
        }
        val confButton = a(href := "#")("Config").render
        confButton.onclick = e => {
          e.preventDefault()
          configDiv.innerHTML = cfg
        }
        tr(td(stopButton), td(idButton), td(s"${new Date(started.toDouble)}"), td(confButton))
    }
    val header = tr(
      th(""),
      th("ID"),
      th("Started"),
      th("Config")
    )
    val render = table((header +: rows): _*).render
  }

  val postForm = div(
    div(
      div(
        h2("Consumers:"),
        tableDiv,
        div(refreshButton),
        configDiv,
        aboutDiv
      )
    )
  ).render

  targetDiv.innerHTML = ""
  targetDiv.appendChild(postForm)
  refresh()
}
