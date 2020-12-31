package expressions.rest.server.record

import io.circe.parser.decode

import java.nio.file.Path

/**
  * Means to play back saved request/responses by [[Recorder]].
  *
  * You can run this as an application which looks at the [[Recorder.savedSessionDir]] for sessions which were dumped
  * via "GET /d/foo" REST calls (typically in a browser)
  *
  * It will generate a .feature file based on the name, as well as 'database-states/<name>' entry for before/after
  * databases which can be loaded/asserted
  */
object FeatureGenerator {

  import eie.io._

  def featuresDir = Recorder.integrationTestResourceDir

  def generateFeatureFromDump(replayDir: Path, camelCaseName: String) = {
    val featureText = gherkin.featureForSession(replayDir, camelCaseName)
    featuresDir.resolve(s"$camelCaseName.feature").text = featureText
  }

  def main(args: Array[String]): Unit = {
    Recorder.integrationTestResourceDir.find(_.fileName == "sessionName.txt").foreach { ssnNameTxt =>
      val camelCaseName = ssnNameTxt.text.split("-", -1).last
      val replayDir     = ssnNameTxt.getParent

      val file = generateFeatureFromDump(replayDir, camelCaseName)

      println(s"Created $file")
    }
  }

  case class RequestOrResponse(file: Path) {
    def fileName = file.fileName

    val requestOpt: Option[Recorder.Request]   = decode[Recorder.Request](file.text).toOption
    val responseOpt: Option[Recorder.Response] = decode[Recorder.Response](file.text).toOption
  }

  private object RequestOrResponse {
    def unapply(file: Path) = {
      file.fileName.split("\\.").headOption.flatMap(_.toIntOption.map(_ => RequestOrResponse(file)))
    }

    def fromDir(dir: Path): Array[RequestOrResponse] = {
      val requestResponses = dir.children.collect {
        case RequestOrResponse(file) => file
      }
      requestResponses.sortBy(_.fileName)
    }
  }

  /**
    * Gherkin parser/formatting
    */
  object gherkin {

    /**
      * This is folded over the request/responses in a directory
      *
      * @param steps
      * @param previous
      */
    case class SessionState(steps: List[String] = Nil, previous: List[RequestOrResponse] = Nil) {
      def lastRequest: Option[Recorder.Request] = previous.collectFirst {
        case rr if rr.requestOpt.isDefined => rr.requestOpt.get
      }

      def previousRequest: Option[Recorder.Request] = previous.headOption.flatMap(_.requestOpt)

      def previousResponse: Option[Recorder.Response] = previous.headOption.flatMap(_.responseOpt)

      def hasPreviousRequest = previousRequest.exists(r => !shouldIgnore(r))

      def isInformPost(request: Recorder.Request): Boolean = {
        request match {
          case Recorder.Post("/inform", _, _) => true
          case _                              => false
        }
      }

      def isLoginPost(request: Recorder.Request): Boolean = {
        request match {
          case Recorder.Post("/login", _, _) => true
          case _                             => false
        }
      }

      def isGetStatus(request: Recorder.Request): Boolean = {
        request match {
          case Recorder.Get("/rest/status", _) => true
          case _                               => false
        }
      }

      def shouldIgnore(request: Recorder.Request): Boolean = {
        isInformPost(request) ||
        isGetStatus(request)
        //        || isLoginPost(request)
      }

      def shouldIncludeInHistory(requestResponse: RequestOrResponse): Boolean = {
        requestResponse.requestOpt.exists(shouldIncludeInHistory) ||
        requestResponse.responseOpt.exists(shouldIncludeInHistory)
      }

      def shouldIncludeInHistory(request: Recorder.Request): Boolean = {
        !isInformPost(request) && !isGetStatus(request)
      }

      def shouldIncludeInHistory(response: Recorder.Response): Boolean = {
        response.status != 404
      }

      def shouldIgnore(response: Recorder.Response): Boolean = {
        response.status == 404 //|| lastRequest.exists(isLoginPost)
      }

      def onRequestResponse(requestResponse: RequestOrResponse) = {
        val next = (requestResponse.requestOpt, requestResponse.responseOpt) match {
          case (Some(request), None)  => onRequest(request)
          case (None, Some(response)) => onResponse(response)
          case other                  => onError(s"THEN we should fix our recorder for $other")
        }
        if (shouldIncludeInHistory(requestResponse)) {

          next.copy(previous = requestResponse :: previous)
        } else {
          next
        }
      }

      def onRequest(request: Recorder.Request) = {
        if (shouldIgnore(request)) {
          this
        } else {
          val prefix = if (hasPreviousRequest) "And " else "When "
          val step   = prefix + requestStep(request)
          copy(steps = step +: steps)
        }
      }

      def onError(errorStep: String) = addStep(errorStep)

      private def addStep(step: String) = copy(steps = step +: steps)

      def onResponse(response: Recorder.Response) = {
        if (shouldIgnore(response)) {
          this
        } else {
          val prefix = if (previousResponse.nonEmpty) "And " else "Then "
          val step   = prefix + responseStep(response)
          copy(steps = step +: steps)
        }
      }

      def asFeatureScenario = steps.reverse.mkString("\n")

    }

    private def capitalize(name: String): String = {
      if (name.isEmpty) ""
      else {
        s"${name.head.toUpper}${name.tail}"
      }
    }

    def splitForCamelCase(name: String): String = {
      val (list, last) = name.foldLeft(List[String]() -> "") {
        case ((list, buffer), c) if c.isUpper => (list :+ buffer, s"$c")
        case ((list, buffer), c)              => (list, buffer + c)
      }
      (list :+ last).map(capitalize).mkString(" ")
    }

    private implicit def richPath(path: Path) = new {
      def copyTo(dir: Path) = {
        dir.resolve(path.fileName).bytes = path.bytes
      }
    }

    def featureForSession(dir: Path, camelCaseName: String) = {
      val requestAndResponses: Array[RequestOrResponse] = RequestOrResponse.fromDir(dir)

      // store the previous database state
      val dbDumpRelativeDir = s"database-states/$camelCaseName"
      locally {
        val previousDumpPath = dir.resolve("previous-state.data").text.asPath
        val dataDir          = featuresDir.resolve(dbDumpRelativeDir).resolve("before").mkDirs()
        previousDumpPath.find(_.fileName.endsWith(".json")).foreach(_.copyTo(dataDir))
      }

      // ... and the 'after' state
      locally {
        val dataDir = featuresDir.resolve(dbDumpRelativeDir).resolve("after").mkDirs()
        dir.find(_.fileName.endsWith(".json")).foreach(_.copyTo(dataDir))
      }

      val requestResponseScenario = asScenario(requestAndResponses)

      val name = splitForCamelCase(camelCaseName)
      s"""Feature: $name
         |
         |Scenario: Request/Responses to make $name
         |
         |Given a database in the initial state like '$dbDumpRelativeDir/before'
         |
         |$requestResponseScenario
         |
         |And the database should look like '$dbDumpRelativeDir/after'
         |""".stripMargin
    }

    def asScenario(requestAndResponses: Array[RequestOrResponse]): String = {
      // fold a consecutive response count and snippet buffer over our request responses
      val session = requestAndResponses.foldLeft(SessionState()) {
        case (state, next) => state.onRequestResponse(next)
      }

      session.asFeatureScenario
    }

    private def docString(body: String) = {
      s"""\"\"\"
         |$body
         |\"\"\"""".stripMargin.linesIterator.map(line => s"  $line").mkString("\n")
    }

    private def requestStep(request: Recorder.Request) = request match {
      case Recorder.Get(route, _) =>
        s"The user GETs request '$route'".stripMargin
      case Recorder.Post(route, _, "") =>
        s"The user POSTs request '$route''".stripMargin
      case Recorder.Post(route, _, body) =>
        s"The user POSTs request '$route' with json\n${docString(body)}".stripMargin
    }

    def headerTable(originalHeaders: Map[String, String]): Option[String] = {
      //| name   | email              | twitter         |
      val filteredHeaders = originalHeaders.filterNot {
        case (_, value) => !value.toLowerCase().contains("redacted")
      }
      if (originalHeaders.isEmpty) {
        None
      } else {

        val keys   = "Key" +: filteredHeaders.keySet.toList.sorted
        val keyLen = ("Key" +: keys).map(_.length).max
        val valLen = ("Value" +: filteredHeaders.values.toList).map(_.length).max

        def pad(key: String, value: String) = s"  | ${key.padTo(keyLen, ' ')} | ${value.padTo(valLen, ' ')} |"

        val entries = keys.map { k =>
          pad(k, filteredHeaders(k))
        }
        Some((pad("Key", "Value") +: entries).mkString("\n"))
      }
    }

    private def responseStep(response: Recorder.Response) = {
      response.body.trim match {
        case "" => s"""they should get a ${response.status} response with headers""".stripMargin
        case body =>
          s"they should get a ${response.status} response with json\n${docString(body)}".stripMargin
      }
    }
  }
}
