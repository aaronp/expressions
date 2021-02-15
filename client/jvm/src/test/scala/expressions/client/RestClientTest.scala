package expressions.client

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.jdk.CollectionConverters._

class RestClientTest extends AnyWordSpec with Matchers with ScalaFutures {

  def testTimeout: FiniteDuration      = 30.seconds
  implicit override def patienceConfig = PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

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
        val actual = RestClient.send(request).futureValue.body
        actual shouldBe """headers:{Hello=[world]} body="abc123""""
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
