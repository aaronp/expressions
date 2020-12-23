package expressions.rest.server

import expressions.client.HttpRequest
import io.circe.syntax.EncoderOps

class ProxyRouteTest extends BaseRouteTest {

  val proxyRequest = HttpRequest
    .get("http://localhost:8080/rest/store/first")
    .withHeader("Accept", "application/json")
    .withHeader("Accept-Encoding", "identity")
    .withHeader("Content-Type", "application/json")

  "ProxyRoute" ignore {
    "be able to proxy requests (manual test - start DevMain first)" in {
      val underTest = ProxyRoute()
      val Some(response) = underTest(
        post(
          "/proxy",
          proxyRequest.asJson.noSpaces
        )).value.value()
      println(response)
      println(s"BODY: >${response.bodyAsString}<")
    }
  }
}
