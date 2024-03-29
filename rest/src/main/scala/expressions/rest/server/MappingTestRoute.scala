package expressions.rest.server

import expressions.CodeTemplate.Expression
import expressions.client.{TransformRequest, TransformResponse}
import expressions.template.{Context, Message}
import expressions.{Cache, DynamicJson}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO, ZIO}

import scala.util.control.NonFatal

object MappingTestRoute {

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  import RestRoutes.Resp
  import RestRoutes.taskDsl._

  def transformHandler(handler: TransformRequest => UIO[Resp]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "mapping" / "check" => req.as[TransformRequest].flatMap(handler)
    }
  }

  def apply(cache: Cache[Expression[JsonMsg, Json]], asContext: JsonMsg => Context[JsonMsg] = _.asContext()): HttpRoutes[Task] = {

    transformHandler {
      case TransformRequest(userInputScript, userInput, key, timestamp, headers, topic, offset, partition) =>
        val inputAsMessage = Message(DynamicJson(userInput), DynamicJson(key), timestamp, headers, topic, offset, partition)

        // we don't want the case-class result but rather its json representation for the check so the 'check' route
        // can displayificate it
        val script =
          s"""
             |import io.circe.syntax._
             |import io.circe.Json
             |
             |${userInputScript}
             |""".stripMargin

        logger.info(s"Checking\n$script")
        ZIO.fromTry(cache(script)).either.map {
          case Left(err: Throwable) =>
            logger.error(s"Error parsing\n$script\n$err")
            val errMsg = s"computer says no:\n${err.getMessage}"
            Response(status = Status.Ok).withEntity(TransformResponse(errMsg.asJson, false, List(errMsg)))
          case Right(mapper: Expression[JsonMsg, Json]) =>
            try {
              val context = asContext(inputAsMessage)
              val mapped  = mapper(context)
              Response(Status.Ok).withEntity(TransformResponse(mapped))
            } catch {
              case NonFatal(e) =>
                logger.error(s"Error executing\n$script\n$e")
                Response(status = Status.Ok)
                  .withEntity(TransformResponse(s"didn't work w/ input: ${e.getMessage}".asJson, false, List(s"didn't work w/ input: ${e.getMessage}")))
            }
        }
    }
  }
}
