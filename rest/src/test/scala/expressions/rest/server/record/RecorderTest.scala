package expressions.rest.server.record

import expressions.rest.server.BaseRouteTest

class RecorderTest extends BaseRouteTest {

  "Recorder" should {
    "understand multiline requests" in {
      val script =
        """val StoreURL = s"http://localhost:8080/rest/store"
                    |val url = s"$StoreURL/${record.topic}/${record.partition}/${record.offset}"
                    |val body = {
                    |  val jason: Json = record.content.jsonValue
                    |  val enrichment = Json.obj(
                    |    "timestamp" -> System.currentTimeMillis().asJson,
                    |    "kafka-key" -> record.key.jsonValue
                    |  )
                    |  jason.deepMerge(enrichment)
                    |}
                    |
                    |val request = HttpRequest.post(url).withBody(body.noSpaces)
                    |List(request)""".stripMargin

      val input =
        s"""HTTP/1.1 POST /rest/store/fool.sc Headers(Host: localhost:8080, Connection: keep-alive, Content-Length: 416, User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36, DNT: 1, Content-Type: application/content, Accept: */*, Origin: http://localhost:8080, Sec-Fetch-Site: same-origin, Sec-Fetch-Mode: cors, Sec-Fetch-Dest: empty, Referer: http://localhost:8080/index.html, Accept-Encoding: gzip, deflate, br, Accept-Language: en-GB,en-US;q=0.9,en;q=0.8, Cookie: <REDACTED>) body="$script"""".stripMargin

      val buffer = Recorder() {
        case (path, name) =>
      }

      buffer.log(input).value()
      val List(Recorder.Post("/rest/store/fool.sc", _, body)) = buffer.requests
      body shouldBe script
    }
  }
}
