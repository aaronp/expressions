package expressions.client

import io.circe.syntax.EncoderOps
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.html.Div
import org.scalajs.dom.{document, window}
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}
@JSExportTopLevel("IndexPage")
case class IndexPage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val scriptTextArea = textarea(cols := 100, rows := 12).render
  scriptTextArea.value = """import expressions.client._
                           |
                           |record.value.hello.world.flatMapSeq { json =>
                           |  json.nested.mapAs { i =>
                           |    json.name.get[String] match {
                           |      case "first" => HttpRequest(method = "GET", url = s"${json.name.string.get}-$i", Map.empty)
                           |      case other   => HttpRequest(method = "POST", url = s"${json.name.string.get}-$i", Map("other" -> other))
                           |    }
                           |  }
                           |}""".stripMargin

  val jsonInputTextArea = textarea(cols := 100, rows := 12).render
  jsonInputTextArea.value = """{
                              |  "hello" : {
                              |    "world" : [
                              |      {
                              |        "name" : "first",
                              |        "nested" : [1,2,3]
                              |      },
                              |      {
                              |        "name" : "second",
                              |        "nested" : [4,5]
                              |      }
                              |    ]
                              |  }
                              |}""".stripMargin

  val resultTextArea = textarea(cols := 100, rows := 8).render

  val clearButton = button("Clear").render
  clearButton.onclick = e => {
    e.preventDefault()
    resultTextArea.value = ""
  }
  val testButton = button("Test").render
  testButton.onclick = e => {
    e.preventDefault()
    makeRequest()
  }

  val testForm = div(
    div(
      h2("Script:"),
      scriptTextArea,
      h2("Message Input:"),
      jsonInputTextArea,
      h2("Result:"),
      resultTextArea,
      div(clearButton, testButton)
    )
  ).render
  targetDiv.innerHTML = ""
  targetDiv.appendChild(testForm)

  def makeRequest(): Unit = {
    io.circe.parser.parse(jsonInputTextArea.value).toTry match {
      case Success(inputJson) =>
        makeRequest(TransformRequest(scriptTextArea.value, inputJson))
      case Failure(err) =>
        resultTextArea.value = s"It looks like you need to fix your json input:\n$err"
    }
  }

  def makeRequest(request: TransformRequest): Unit = {
    val url = s"${window.document.location.protocol}//${window.document.location.host}/rest/check"
    Ajax.post(url, request.asJson.noSpaces, headers = Map("Content-Type" -> "application/json")).onComplete {
      case Success(response) =>
        try io.circe.parser.decode[TransformResponse](response.responseText).toTry match {
          case Success(response) =>
            response.messages.foreach { err =>
              window.alert(err)
            }
            resultTextArea.value = response.result.spaces4
          case _ => resultTextArea.value = response.responseText
        }
      case Failure(err) =>
        resultTextArea.value = err.getMessage
    }
  }
}
