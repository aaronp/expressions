package expressions.client

import expressions.client.kafka.PostRecord
import io.circe.Json
import io.circe.parser.parse
import org.scalajs.dom.html.Div
import org.scalajs.dom.{document, window}
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success, Try}

@JSExportTopLevel("KafkaPostPage")
case class KafkaPostPage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val keyTextArea = textarea(cols := 140, rows := 4).render
  keyTextArea.value = """key{{i}}""".stripMargin

  val recordTextArea = textarea(cols := 140, rows := 20).render
  recordTextArea.value = """{
                           |  "data{{i}}" : "value-{{i}}",
                           |  "flag" :  true
                           |}""".stripMargin

  val configTextArea    = textarea(cols := 140, rows := 20).render
  val repeatText        = input(`type` := "text", value := "1").render
  val topicOverrideText = input(`type` := "text", value := "").render
  val partitionText     = input(`type` := "text", value := "").render

  val postButton = button("Post").render
  postButton.onclick = e => {
    e.preventDefault()
    Client.kafka.publish(postRecord).onComplete {
      case Success(n) =>
        window.alert(s"Posted: $n")
      case Failure(err) => window.alert(s"Oops: $err")
    }

  }
  val refreshButton = button("Refresh").render
  refreshButton.onclick = e => {
    e.preventDefault()
    refreshConfig()
  }

  def refreshConfig() = Client.config.get().foreach(updateConfig)
  def updateConfig(cfg: String) = {
    configTextArea.value = cfg
  }

  def keyJson = asJson(keyTextArea.value)

  def bodyJson = asJson(recordTextArea.value)
  def asJson(text: String): Json = {
    parse(text).toTry.getOrElse {
      if (text.trim.isEmpty) {
        Json.Null
      } else {
        Json.fromString(text)
      }
    }
  }

  def repeatNum = Try(repeatText.value.toInt).getOrElse {
    repeatText.value = "1"
    1
  }
  def partitionNum       = partitionText.value.toIntOption
  def topicOverrideValue = Option(topicOverrideText.value).map(_.trim).filterNot(_.isEmpty)

  def postRecord = PostRecord(
    data = bodyJson,
    config = configTextArea.value,
    key = keyJson,
    repeat = repeatNum,
    partition = partitionNum,
    topicOverride = topicOverrideValue,
    headers = Map.empty
  )

  val postForm = div(
    div(
      div(
        h2("Kafka:"),
        div("If repeat is specified, then the text '{{i}}' will be replaced w/ the right index"),
        label(`for` := keyTextArea.id)("Key:"),
        div(keyTextArea),
        label(`for` := recordTextArea.id)("Value:"),
        div(recordTextArea),
        div(label(`for` := repeatText.id)("Repeat:"), span(repeatText)),
        div(label(`for` := topicOverrideText.id)("Topic:"), span(topicOverrideText)),
        div(
          label(`for` := partitionText.id)("Partition:"),
          span(partitionText)
        ),
        label(`for` := configTextArea.id)("Config:"),
        div(configTextArea),
        div(postButton, refreshButton)
      )
    )
  ).render

  targetDiv.innerHTML = ""
  targetDiv.appendChild(postForm)
  refreshConfig()
}
