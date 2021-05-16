package expressions.rest.server.record

import java.nio.file.Path
import java.time.LocalDateTime

import eie.io._
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor, Json}
import zio.Task

import scala.collection.mutable.ListBuffer

/**
  * Code for creating a request/response http4s logger function (e.g. String => Task[Unit]) which writes down
  * the requests/responses to a buffer.
  *
  * a call to 'GET /d/foo' will NOT match our app route, but when the 404 is logged we drain the buffer to
  * a local directory which can be read back from the 'Playback' test code
  */
object Recorder {
  private val GetR      = """.*HTTP/1\.1 GET (.*?) Headers\((.*)\) body=""""".r
  val PostR             = """(?s).*HTTP/1\.1 POST (.*?) Headers\((.*)\) body="(.*)"""".r
  private val ResponseR = """(?s).*HTTP/1\.1 (\d\d\d).* Headers\((.*)\) body="(.*)"""".r
  private val HeaderR   = "(.*): (.*)".r

  /**
    * @param session
    * @param onDump a callback when dumping a session
    * @return a RecorderBuffer which will write down http request/responses based on the http4s logging
    */
  def apply(session: Long = System.currentTimeMillis())(onDump: (Path, String) => Unit) = new Buffer(session, onDump)

  sealed trait Request {
    def requestAsJson: Json
  }

  object Request {
    given encoder: Encoder[Request] = Encoder.instance[Request] {
      case get: Get   => Get.encoder(get)
      case post: Post => Post.encoder(post)
    }

    given decoder : Decoder[Request] with {
      override def apply(c: HCursor): Result[Request] = {
        Post.decoder
          .tryDecode(c)
          .orElse(
            Get.decoder.tryDecode(c)
          )
      }
    }

  }

  private def parseHeaders(str: String): Map[String, String] = {
    val headers = str.split(";", -1).flatMap {
      case HeaderR(k, v) => Some((k.trim, v.trim))
      case _             => None
    }
    headers.toMap.ensuring(_.size == headers.size)
  }

  private def fmtHeaders(headers: Map[String, String]) = {
    headers.toSeq
      .sortBy(_._1)
      .map {
        case (k, v) => s"$k : $v"
      }
      .mkString("\n\t", "\n\t", "\n")
  }

  case class Get(route: String, headers: Map[String, String]) extends Request {
    override def requestAsJson: Json = Get.encoder(this)

    override def toString = s"""GET $route${fmtHeaders(headers)}"""
  }

  object Get {
    given encoder : Encoder[Get] = io.circe.generic.semiauto.deriveEncoder[Get].mapJsonObject(_.add("method", "GET".asJson))
    given decoder : Decoder[Get] = io.circe.generic.semiauto.deriveDecoder[Get]
  }

  case class Post(route: String, headers: Map[String, String], body: String) extends Request {
    override def requestAsJson: Json = Post.encoder(this)

    override def toString = s"""POST $route${fmtHeaders(headers)}\nbody=>>>$body<<<\n"""
  }

  object Post {
    given encoder : Encoder[Post] = io.circe.generic.semiauto.deriveEncoder[Post].mapJsonObject(_.add("method", "POST".asJson))
    given decoder : Decoder[Post] = io.circe.generic.semiauto.deriveDecoder[Post]
  }

  case class Response(status: Int, headers: Map[String, String], body: String) {
    override def toString = s"""Response $status: ${fmtHeaders(headers)}$body\n"""
  }

  object Response {
    given encoder : Encoder[Response] = io.circe.generic.semiauto.deriveEncoder[Response]
    given decoder : Decoder[Response] = io.circe.generic.semiauto.deriveDecoder[Response]
  }

  def savedSessionDir: Path = integrationTestResourceDir.resolve("savedSessions")

  def integrationTestResourceDir: Path = {
    val paths = List("./", "../", "../../").map { parent =>
      s"$parent/rest/src/test/resources".asPath
    }
    paths.find(_.isDir).getOrElse(sys.error("Couldn't seem to find the fucking integration-test src resources dir"))
  }

  class Buffer(sessionId: Long, onDumpCallback: (Path, String) => Unit, limit: Int = 100) {

    private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
    private val buffer = ListBuffer[Either[Request, Response]]()

    private def pad(n: Int) = n.toString.reverse.padTo(3, '0').reverse

    def append(request: Request): Unit = {
      append(Left(request))
      logger.info(s"RECORDER (${buffer.size}): $request")
    }

    def append(response: Response): Unit = {
      append(Right(response))
      logger.info(s"RECORDER RESPONSE (${buffer.size}): $response")
    }

    private def append(record: Either[Request, Response]): Unit = {
      if (record != null) {
        buffer += record
      }
      if (buffer.size > limit) {
        buffer.remove(0)
      }
    }

    def requests =
      buffer.collect {
        case Left(r) => r
      }.toList

    /**
      * We have a 'fake' GET /d/xxx route which doesn't match any of our routes, but we use here to
      * dump out our requests/responses in a test
      *
      * @return the current session as a test
      */
    def dumpTest(name: String) = {
      val timestamp = LocalDateTime.now().toString.replace(":", "_")
      val dir: Path = savedSessionDir.resolve(s"$sessionId-$name/${timestamp}-${buffer.size}").mkDirs()
      dir.resolve("sessionName.txt").text = s"$sessionId-$name"

      buffer.zipWithIndex.foreach {
        case (Left(request), i)   => dir.resolve(pad(i) + ".request").text = request.requestAsJson.noSpaces
        case (Right(response), i) => dir.resolve(pad(i) + ".response").text = Response.encoder(response).noSpaces
      }
      buffer.clear()

      onDumpCallback(dir, name)

      logger.info(s"Dumped session to $dir")
    }

    private val DumpR = "/d/?(.*)".r

    def log(msg: String): Task[Unit] = {
      Task.effect {
        msg match {
          case GetR(DumpR(name), _) =>
            if (name == "") {
              dumpTest("anon")
            } else {
              dumpTest(name)
            }
          case GetR(route, h) if route.startsWith("/rest") => append(Get(route, parseHeaders(h)))
          case GetR(route, h)                              => logger.debug(s"IGNORING non-rest: ${Get(route, parseHeaders(h))}")
          case PostR(route, h, body) =>
            val headers: Map[String, String] = parseHeaders(h)
            if (!headers.get("User-Agent").exists(_.startsWith("AirControl Agent"))) {
              append(Post(route, parseHeaders(h), body))
            } else {
              logger.info(headers.mkString(s"IGNORING ${Post(route, headers, body)}"))
            }
          case ResponseR(status, h, body) =>
            val headers: Map[String, String] = parseHeaders(h)
            logger.info(headers.mkString(s"parsing response w/ headers >$h<:\n\t", "\n\t", "\n"))
            append(Response(status.toInt, headers, body))
          case _ =>
            logger.info(s"_HTTP_: didn't understand >$msg<")
        }
      }
    }
  }
}
