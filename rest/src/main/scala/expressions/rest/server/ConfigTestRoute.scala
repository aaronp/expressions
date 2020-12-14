package expressions.rest.server

import args4c.StringEntry
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import expressions.StringTemplate.StringExpression
import expressions.client.{TransformRequest, TransformResponse}
import expressions.template.{Context, Message}
import expressions.{Cache, RichDynamicJson, StringTemplate}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO, ZIO}

/**
  * A handler for:
  * {{{
  *   POST config/check
  * }}}
  *
  * whose 'script' is actually assumed to be a typesafe config whose values may be '{{ ... }}' placeholders
  */
object ConfigTestRoute extends StrictLogging {

  import RestRoutes.Resp
  import RestRoutes.taskDsl._

  def makeRoute(handler: TransformRequest => UIO[Resp]) = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "check" => req.as[TransformRequest].flatMap(handler)
    }
  }

  /**
    * What we want to do is transform some kind of template - e.g.
    * @param asContext
    * @return
    */
  def apply(asContext: Message[RichDynamicJson] => Context[RichDynamicJson] = _.asContext()): HttpRoutes[Task] = {
    val expressionForString: Cache[StringExpression[RichDynamicJson]] = StringTemplate.newCache[RichDynamicJson]("implicit val _implicitJsonValue = record.value.jsonValue")

    makeRoute { request =>
      mapEntries(request, expressionForString, asContext).either.map {
        case Left(e) =>
          val errorMessage = s"didn't work w/ input: ${e.getMessage}"
          logger.error(errorMessage, e)
          Response(status = Status.InternalServerError)
            .withEntity(TransformResponse(errorMessage.asJson, Some(s"didn't work w/ input: ${e.getMessage}")))
        case Right(keyValues) =>
          val mapped = Json.obj(keyValues.map {
            case (key, value) => (key, value.asJson)
          }: _*)
          Response(Status.Ok).withEntity(TransformResponse(mapped, None))
      }
    }
  }

  private def mapEntries(request: TransformRequest,
                         expressionForString: Cache[StringExpression[RichDynamicJson]],
                         asContext: Message[RichDynamicJson] => Context[RichDynamicJson]): ZIO[Any, Throwable, Seq[(String, String)]] = {

    def eval(entry: StringEntry, ctxt: Context[RichDynamicJson]) = {
      for {
        script        <- Task.fromTry(expressionForString(entry.value))
        scriptedValue <- Task(script(ctxt))
      } yield (entry.key, scriptedValue)
    }

    val TransformRequest(configString, userInputJson, key, timestamp, headers) = request
    import args4c.implicits._
    val userInput = new RichDynamicJson(userInputJson)

    for {
      // read the input  string as a configuration
      config <- appConfig(configString)
      // map each value ...
      entries <- Task.foreach(config.summaryEntries()) {
        // ... we think it's some kind of script if there's a ' .. {{ .. }} .. '
        case entry if entry.value.contains("{{") && entry.value.contains("}}") =>
          val ctxt = asContext(Message(userInput, key, timestamp, headers))
          eval(entry, ctxt)
        // otherwise just use the key/value as-is
        case entry => Task.succeed(entry.key -> entry.value)
      }
    } yield entries
  }

  private def appConfig(configString: String) = Task.effect {
    ConfigFactory
      .parseString(configString)
      .withFallback(ConfigFactory.load().getConfig("app"))
  }

}
