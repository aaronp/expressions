package expressions.rest.server

import com.typesafe.config.ConfigFactory
import io.circe.Decoder
import org.http4s._
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import zio.duration.{Duration, durationInt}
import zio.interop.catz._
import zio.{Task, ZEnv, ZIO}

import java.nio.file.{Files, Path, Paths}
import java.util.UUID

abstract class BaseRouteTest extends AnyWordSpec with Matchers with GivenWhenThen with Eventually with ScalaFutures {

  extension (json: String)
    def jason = io.circe.parser.parse(json).toTry.get

  given rt: zio.Runtime[ZEnv] = zio.Runtime.default

  def zenv = rt.environment

  def testTimeout: Duration = 1000.seconds

  def shortTimeoutJava = 200.millis

  extension [A](zio: => ZIO[ZEnv, Any, A])(using rt: _root_.zio.Runtime[_root_.zio.ZEnv])
    def value(): A = rt.unsafeRun(zio.timeout(testTimeout)).getOrElse(sys.error("Test timeout"))

  def withTmpDir[A](f: java.nio.file.Path => A) = {
    val dir = Paths.get(s"./target/${getClass.getSimpleName}-${UUID.randomUUID()}")
    try {
      Files.createDirectories(dir)
      f(dir)
    } finally {
      delete(dir)
    }
  }

  def delete(dir: Path): Unit = {
    if (Files.isDirectory(dir)) {
      Files.list(dir).forEach(delete)
    }
    Files.delete(dir)
  }

  def testConfig() = ConfigFactory.load()

  implicit override def patienceConfig = PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  extension (response: Response[Task])(using rt: zio.Runtime[zio.ZEnv])
    def bodyTask: Task[String] = EntityDecoder.decodeText(response)
    def bodyAsString: String  = bodyTask.value()
    def bodyAs[A: Decoder]: A = io.circe.parser.parse(bodyAsString).toTry.flatMap(_.as[A].toTry).get

  extension (route: HttpRoutes[Task])
    def responseFor(request: Request[Task]): Response[Task] = responseForOpt(request).getOrElse(sys.error("no response"))
    def responseForOpt(request: Request[Task]): Option[Response[Task]] = route(request).value.value()

  def get(url: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.GET, uri = uri)
  }

  def post(url: String, body: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.POST, uri = uri).withEntity(body)
  }
  def post(url: String): Request[Task] = Request[Task](method = Method.POST, uri = asUri(url))

  def asUri(url: String, queryParams: (String, String)*) = {
    val encoded = Uri.encode(url)
    val uri = if (queryParams.isEmpty) {
      Uri.unsafeFromString(encoded)
    } else {
      Uri.unsafeFromString(encoded).withQueryParams(queryParams.toMap)
    }
    uri
  }

  def rnd(prefix: String = "") = {
    val x = UUID.randomUUID().toString.filter(_.isLetterOrDigit).toLowerCase()
    s"${prefix}$x"
  }

  def exampleAvro = """[
                      |{
                      |  "namespace": "example",
                      |  "type": "record",
                      |  "name": "Example",
                      |  "fields": [
                      |    {
                      |      "name": "id",
                      |      "type": "string",
                      |      "default" : ""
                      |    },
                      |    {
                      |      "name": "addresses",
                      |      "type": {
                      |          "type": "array",
                      |          "items":{
                      |              "name":"Address",
                      |              "type":"record",
                      |              "fields":[
                      |                  { "name":"name", "type":"string" },
                      |                  { "name":"lines", "type": { "type": "array", "items" : "string"} }
                      |              ]
                      |          }
                      |      }
                      |    },
                      |    {
                      |      "name": "someText",
                      |      "type": "string",
                      |      "default" : ""
                      |    },
                      |    {
                      |      "name": "someLong",
                      |      "type": "long",
                      |      "default" : 0
                      |    },
                      |    {
                      |      "name": "someDouble",
                      |      "type": "double",
                      |      "default" : 1.23
                      |    },
                      |    {
                      |      "name": "someInt",
                      |      "type": "int",
                      |      "default" : 123
                      |    },
                      |    {
                      |      "name": "someFloat",
                      |      "type": "float",
                      |      "default" : 1.0
                      |    },
                      |    {
                      |      "name": "day",
                      |      "type": {
                      |        "name": "daysOfTheWeek",
                      |        "type": "enum",
                      |        "symbols": [ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" ]
                      |      },
                      |      "default" : "MONDAY"
                      |    }
                      |  ]
                      |}
                      |
                      |]""".stripMargin
}
