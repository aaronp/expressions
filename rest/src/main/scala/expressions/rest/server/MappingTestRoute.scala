package expressions.rest.server

import com.typesafe.scalalogging.StrictLogging
import expressions.JsonTemplate.Expression
import expressions.client.{TransformRequest, TransformResponse}
import expressions.template.{Context, Message}
import expressions.{Cache, JsonTemplate, RichDynamicJson}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO, ZIO}

import scala.util.control.NonFatal

object MappingTestRoute extends StrictLogging {

  import RestRoutes.Resp
  import RestRoutes.taskDsl._

  def transformHandler(handler: TransformRequest => UIO[Resp]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "mapping" / "check" => req.as[TransformRequest].flatMap(handler)
    }
  }

  def apply(asContext: Message[RichDynamicJson] => Context[RichDynamicJson] = _.asContext()): HttpRoutes[Task] = {
    val cache: Cache[Expression[RichDynamicJson, Json]] = JsonTemplate.newCache[Json]
    transformHandler {
      case TransformRequest(userInputScript, userInput, key, timestamp, headers) =>
        val inputAsMessage = Message(new RichDynamicJson(userInput), key, timestamp, headers)

        // we don't want the case-class result but rather its json representation for the check so the 'check' route
        // can displayificate it
        val script =
          s"""
             |import io.circe.syntax._
             |
             |val __userEndResult = {
             |   ${userInputScript}
             |}
             |
             |__userEndResult.asJson
             |""".stripMargin

        logger.info(s"Checking\n$script")
        ZIO.fromTry(cache(script)).either.map {
          case Left(err: Throwable) =>
            logger.error(s"Error parsing\n$script\n$err")
            Response(status = Status.InternalServerError).withEntity(TransformResponse(s"nope: ${err.getMessage}".asJson, Some(s"nope: ${err.getMessage}")))
          case Right(mapper: Expression[RichDynamicJson, Json]) =>
            try {
              val context = asContext(inputAsMessage)
              val mapped  = mapper(context)
              Response(Status.Ok).withEntity(TransformResponse(mapped, None))
            } catch {
              case NonFatal(e) =>
                logger.error(s"Error executing\n$script\n$e")
                Response(status = Status.InternalServerError)
                  .withEntity(TransformResponse(s"didn't work w/ input: ${e.getMessage}".asJson, Some(s"didn't work w/ input: ${e.getMessage}")))
            }
        }
    }
  }
}
