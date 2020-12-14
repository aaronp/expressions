package expressions.client

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class RestClientTest extends AnyWordSpec with Matchers {
  "RestClient" should {
    "be able to send requests" in {
      val localServer = SimpleHttpServer.start(0, Map("/echo" -> RestClientTest.echoHandler))

      val openPort = localServer.getAddress.getPort
      try {
        val request = HttpRequest
          .post(s"http://localhost:$openPort/echo")
          .withHeader("hello", "world")
          .withBody("abc123")

        // call the method under test
        RestClient.send(request).body shouldBe Right("headers:{Hello=[world]} body=abc123")
      } finally {
        // the 'stop' can hang, so we kill it in another fiber
        Future {
          localServer.stop(1000)
        }
      }
    }
  }
}

object RestClientTest {

  val echoHandler = SimpleHttpServer.handler { ctxt =>
    val src = scala.io.Source.fromInputStream(ctxt.getRequestBody)

    val body = try {
      src.getLines().mkString("")
    } finally {
      src.close()
    }

    def has(key: String) = key.toLowerCase().startsWith("hello")
    val headers = ctxt.getRequestHeaders.asScala.collect {
      case (key, values) if has(key) => values.asScala.mkString(s"$key=[", ",", "]")
    }
    headers.mkString("headers:{", ";", s"} body=${body}")
  }
}
