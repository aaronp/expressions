package expressions.rest.server

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

  implicit val rt: zio.Runtime[ZEnv] = zio.Runtime.default

  def zenv = rt.environment

  def testTimeout: Duration = 30.seconds

  def shortTimeoutJava = 200.millis

  implicit def asRichZIO[A](zio: => ZIO[ZEnv, Any, A])(implicit rt: _root_.zio.Runtime[_root_.zio.ZEnv]) = new {
    def value(): A = rt.unsafeRun(zio.timeout(testTimeout)).getOrElse(sys.error("Test timeout"))
  }

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

  implicit override def patienceConfig = PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  implicit def forResponse(response: Response[Task])(implicit rt: zio.Runtime[zio.ZEnv]) = new {

    def bodyTask: Task[String] = EntityDecoder.decodeText(response)

    def bodyAsString: String  = bodyTask.value()
    def bodyAs[A: Decoder]: A = io.circe.parser.parse(bodyAsString).toTry.flatMap(_.as[A].toTry).get
  }

  implicit class RichRoute(route: HttpRoutes[Task]) {
    def responseFor(request: Request[Task]): Response[Task] = responseForOpt(request).getOrElse(sys.error("no response"))

    def responseForOpt(request: Request[Task]): Option[Response[Task]] = {
      route(request).value.value()
    }
  }

  def get(url: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.GET, uri = uri)
  }

  def post(url: String, body: String, queryParams: (String, String)*): Request[Task] = {
    val uri: Uri = asUri(url, queryParams: _*)
    Request[Task](method = Method.POST, uri = uri).withEntity(body)
  }

  private def asUri(url: String, queryParams: (String, String)*) = {
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
}
