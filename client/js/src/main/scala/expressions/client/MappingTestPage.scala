package expressions.client

import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.html.Div
import org.scalajs.dom.{document, window}
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

@JSExportTopLevel("MappingTestPage")
case class MappingTestPage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val scriptTextArea = textarea(cols := 100, rows := 12).render
  scriptTextArea.value = """import expressions.client._
                           |
                           |record.value.hello.world.flatMapSeq { json =>
                           |  json.nested.mapAs { i =>
                           |    json.name.get[String] match {
                           |      case "first" => HttpRequest.get(s"${json.name.string.get}-$i")
                           |      case other   => HttpRequest.post(s"${json.name.string.get}-$i", Map("other" -> other))
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
    Client.mapping.check(request).onComplete {
      case Success(response) =>
        response.messages.foreach { err =>
          window.alert(err)
        }
        resultTextArea.value = response.result.spaces4
      case Failure(err: AjaxException) =>
        resultTextArea.value = AsError(err.xhr.responseText)
      case Failure(err) =>
        resultTextArea.value = s"Error: ${err.getMessage}\n${err}"
    }
  }
}
