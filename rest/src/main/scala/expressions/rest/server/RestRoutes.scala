package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.JsonTemplate.Expression
import expressions.client.{TransformRequest, TransformResponse}
import expressions.template.{Context, Message}
import expressions.{Cache, JsonTemplate, RichDynamicJson, StringTemplate}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s
import org.http4s.circe.CirceEntityCodec._
import org.http4s.dsl.Http4sDsl
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO, ZIO}

import scala.util.control.NonFatal

/**
  * You've got to have a little fun.
  */
object RestRoutes extends StrictLogging {

  type Resp = http4s.Response[Task]

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]
  import taskDsl._

  def apply(defaultConfig : Config = ConfigFactory.load()): HttpRoutes[Task] = {
    transformRoute() <+> configRoute(defaultConfig)
  }


  def transformRoute(): HttpRoutes[Task] = {
    val cache: Cache[Expression[RichDynamicJson, Json]] = JsonTemplate.newCache[Json]
    transformHandler {
      case TransformRequest(userInputScript, userInput) =>
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
              val context = Message(new RichDynamicJson(userInput)).asContext()
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
  def configRoute(defaultConfig : Config): HttpRoutes[Task] = {

    val template = StringTemplate.newCache[Context[RichDynamicJson]]

    val cache: Cache[Expression[RichDynamicJson, Json]] = JsonTemplate.newCache[Json]
    transformHandler {
      case TransformRequest(configString, userInput) =>
        import args4c.implicits._

        val ctxt = Message(new RichDynamicJson(userInput)).withKey("foo").asContext()
        try {
          val config = ConfigFactory.parseString(configString).withFallback(ConfigFactory.load())

          val entries = config.summaryEntries {
            case (key, value) =>
              template.apply(value)
          }
          entries.map { entry =>
            s"${entry.key} : ${entry.value}"
          }

        } catch {
          case NonFatal(e) =>
        }

        logger.info(s"Checking\n$script")
        ZIO.fromTry(cache(script)).either.map {
          case Left(err: Throwable) =>
            logger.error(s"Error parsing\n$script\n$err")
            Response(status = Status.InternalServerError).withEntity(TransformResponse(s"nope: ${err.getMessage}".asJson, Some(s"nope: ${err.getMessage}")))
          case Right(mapper: Expression[RichDynamicJson, Json]) =>
            try {
              val context = Message(new RichDynamicJson(userInput)).asContext()
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

  def transformHandler(handler: TransformRequest => UIO[Resp]) = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "check" => req.as[TransformRequest].flatMap(handler)
    }
  }

  def configHandler(handler: TransformRequest => UIO[Resp]) = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" => req.as[TransformRequest].flatMap(handler)
    }
  }
}
