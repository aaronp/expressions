package expressions.client

import io.circe.Json
import io.circe.parser.parse
import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.html.Div
import org.scalajs.dom.{document, window}
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.scalajs.js.Date
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.{Failure, Success}

@JSExportTopLevel("IndexPage")
case class IndexPage(targetDivId: String) {

  val targetDiv = document.getElementById(targetDivId).asInstanceOf[Div]

  val configTextArea    = textarea(cols := 140, rows := 20).render
  val configNameTextBox = input(`type` := "text", id := "config-text", size := 120, value := "application.conf").render
  val saveConfigButton  = button("Save").render

  def asPath(str: String) = str.split("/", -1).map(_.trim).toList
  def configPath          = asPath(configNameTextBox.value)

  saveConfigButton.onclick = e => {
    e.preventDefault()
    Client.disk.storeAt(configNameTextBox.value, configTextArea.value)
  }

  val mappingDiv         = div().render
  val reloadConfigButton = button("Refresh").render

  def read(path: List[String])(handler: Option[String] => Unit) = {
    Client.disk.read(path).onComplete {
      case Success(opt) => handler(opt)
      case Failure(err) => window.alert(s"${path.mkString("/")} returned $err")
    }
  }
  reloadConfigButton.onclick = e => {
    e.preventDefault()
    configPath match {
      case Nil | List("") => Client.config.get().foreach(updateConfig)
      case path =>
        read(path) {
          case None =>
            window.alert(s"No config found for ${path.mkString("/")}")
          case Some(found) => updateConfig(found)
        }
    }
  }

  def defaultScript = """record.value.hello.world.flatMapSeq { json =>
                        |  json.nested.mapAs { i =>
                        |    json.name.get[String] match {
                        |      case "first" => HttpRequest.get(s"${json.name.string.get}-$i")
                        |      case other   => HttpRequest.post(s"${json.name.string.get}-$i", Map("other" -> other))
                        |    }
                        |  }
                        |}""".stripMargin

  val scriptTextArea = textarea(cols := 100, rows := 12).render
  scriptTextArea.value = ""

  val topicInputText = input(`type` := "text", id := "topic", size := 20, value := "some-topic").render
  val keyInputText   = input(`type` := "text", id := "key", size := 20, value := "some-key").render

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

  val mappingResultTextArea = textarea(cols := 100, rows := 8).render

  val startButton = button("Start").render
  startButton.onclick = e => {
    e.preventDefault()
    window.location.href = s"${Client.remoteHost}/running.html"
  }

  val clearButton = button("Clear").render
  clearButton.onclick = e => {
    e.preventDefault()
    mappingResultTextArea.value = ""
    execResponseDiv.innerHTML = ""
  }

  val execResponseDiv   = div().render
  val execRequestButton = button("Execute").render

  execRequestButton.onclick = e => {
    e.preventDefault()
    executeRequests()
  }

  val testMappingButton = button("Test Mapping").render
  testMappingButton.onclick = e => {
    e.preventDefault()
    checkTransform()
  }

  object chooseTopic {
    def refresh(cfg: String = configTextArea.value): Future[chooseTopic] = {
      Client.config.listMappings(cfg).map { byTopic =>
        val inst = chooseTopic(byTopic)
        mappingDiv.innerHTML = ""
        mappingDiv.appendChild(inst.render)
        inst.refreshMapping()
        inst
      }
    }
  }

  /**
    * The topic -> mapping functions
    * @param topics
    */
  case class chooseTopic(topics: Map[String, List[String]]) {
    val options = topics.toSeq.sortBy(_._1).map {
      case (topic, path) =>
        option(value := path.mkString("/"))(topic)
    }

    val choice = select(name := "Mappings", id := "mappings")(
      options: _*
    ).render

    val refreshButton = button("Refresh").render
    refreshButton.onclick = e => {
      e.preventDefault()
      chooseTopic.refresh()
    }

    def currentPath   = asPath(options(choice.selectedIndex).render.value)
    def selectedTopic = options(choice.selectedIndex).render.innerText

    choice.onchange = (e) => {
      e.preventDefault()
      refreshMapping(currentPath)
    }

    def refreshMapping(value: List[String] = currentPath) = {
      Client.disk.read(value).onComplete {
        case Success(None)         => scriptTextArea.value = defaultScript
        case Success(Some(script)) => scriptTextArea.value = script
        case Failure(_) =>
          window.alert(s"No mapping found for ${selectedTopic} at path ${currentPath.mkString("/")}")
          scriptTextArea.value = defaultScript
      }
    }

    val saveMappingButton = button("Save Mapping").render
    saveMappingButton.onclick = e => {
      e.preventDefault()
      Client.disk.store(currentPath, scriptTextArea.value)
    }
    val render = div(
      label(`for` := "topicMappings")("Mapping For:"),
      choice,
      div(refreshButton, saveMappingButton)
    ).render
  }

  def updateConfig(cfg: String) = {
    configTextArea.value = cfg
    chooseTopic.refresh(cfg).foreach { choice =>
      mappingDiv.innerHTML = ""
      mappingDiv.appendChild(choice.render)
    }
  }
  Client.config.get().foreach(updateConfig)

  val testForm = div(
    div(
      div(
        h2("Config:"),
        label(`for` := configNameTextBox.id)("Config:"),
        div(configNameTextBox),
        div(saveConfigButton, reloadConfigButton),
        configTextArea
      ),
      h2("Topics/Mappings:"),
      div(
        mappingDiv,
        scriptTextArea
      ),
      div(testMappingButton),
      h2("Test Input:"),
      div(
        label(`for` := topicInputText.id)("Topic:"),
        div(topicInputText)
      ),
      div(
        label(`for` := keyInputText.id)("Key:"),
        keyInputText
      ),
      div(jsonInputTextArea),
      h2("Result:"),
      mappingResultTextArea,
      div(clearButton, execRequestButton),
      execResponseDiv
    )
  ).render
  targetDiv.innerHTML = ""
  targetDiv.appendChild(testForm)

  def executeRequests(requests: List[HttpRequest]): Unit = {
    val orderedList = ol().render
    execResponseDiv.appendChild(h3(s"Making ${requests.size} Requests:").render)
    execResponseDiv.appendChild(orderedList)
    requests.foreach { in =>
      Client.proxy.makeRequest(in).map { resp =>
        val result = li(
          ul(
            li(span(s"${in.method} ${in.url} =>"), a(href := s"${in.url}")(s"${in.method} ${in.url} =>")),
            li(s"${resp.statusCode} : ${resp.body}")
          )
        )
        orderedList.appendChild(result.render)
      }
    }
  }

  def executeRequests(): Unit = {
    execResponseDiv.innerHTML = ""
    parse(mappingResultTextArea.value).toTry match {
      case Success(json) =>
        json.as[List[HttpRequest]].toTry match {
          case Success(requests) =>
            executeRequests(requests)
          case Failure(err) =>
            execResponseDiv.innerHTML = ""
            execResponseDiv.appendChild(pre(s"It looks like you need to fix your http request:\n$err").render)
        }
      case Failure(err) =>
        execResponseDiv.innerHTML = ""
        execResponseDiv.appendChild(pre(s"The mapping requests aren't valid json:\n$err").render)
    }
  }

  def transformRequest(inputJson: Json): TransformRequest =
    TransformRequest(scriptTextArea.value, inputJson, key = Json.fromString(keyInputText.value), topic = topicInputText.value, timestamp = (new Date().getTime()).toLong)

  def checkTransform(): Unit = {
    parse(jsonInputTextArea.value).toTry match {
      case Success(inputJson) => checkTransform(transformRequest(inputJson))
      case Failure(err) =>
        mappingResultTextArea.innerHTML = s"It looks like you need to fix your json input:\n$err"
    }
  }

  def checkTransform(request: TransformRequest): Unit = {
    Client.mapping.check(request).onComplete {
      case Success(response) =>
        response.messages.foreach(window.alert)
        mappingResultTextArea.value = response.result.spaces4
      case Failure(err: AjaxException) =>
        mappingResultTextArea.value = AsError(err.xhr.responseText)
      case Failure(err) =>
        mappingResultTextArea.value = s"Error: ${err.getMessage}\n${err}"
    }
  }
}
