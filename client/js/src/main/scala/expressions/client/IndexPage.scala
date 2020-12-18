package expressions.client

import org.scalajs.dom.ext.AjaxException
import org.scalajs.dom.html.Div
import org.scalajs.dom.{document, window}
import scalatags.JsDom.all._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
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

  val startButton = button("Start").render
  startButton.onclick = e => {
    e.preventDefault()
    window.location.href = s"${Client.remoteHost}/running.html"
  }

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
      div(testButton),
      h2("Test Input:"),
      div(jsonInputTextArea),
      h2("Result:"),
      resultTextArea,
      div(clearButton)
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
        response.messages.foreach(window.alert)
        resultTextArea.value = response.result.spaces4
      case Failure(err: AjaxException) =>
        resultTextArea.value = AsError(err.xhr.responseText)
      case Failure(err) =>
        resultTextArea.value = s"Error: ${err.getMessage}\n${err}"
    }
  }
}
