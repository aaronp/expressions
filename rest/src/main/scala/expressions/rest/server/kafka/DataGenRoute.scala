package expressions.rest.server.kafka

import cats.implicits.*
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.client.kafka.PostRecord
import expressions.franz.{DataGen, FranzConfig, SchemaGen}
import expressions.rest.server.*
import expressions.rest.server.RestRoutes.taskDsl.*
import expressions.rest.server.kafka.KafkaPublishRoute.OptionalSeed
import io.circe.*
import io.circe.syntax.*
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import org.apache.avro.Schema
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.multipart.Multipart
import org.http4s.{HttpRoutes, Request, Response, Status}
import org.slf4j.LoggerFactory
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz.*
import zio.{Task, UIO, ZIO}

import scala.util.Try

/**
  * Use cases are to create test data from:
  * $ an avro file update - covered by ```POST /data/parse``` an avro file
  * $ a json file upload (just echo the json back)
  * $ hocon text (e.g. parse the config and return it as json)
  * $ a kafka topic (keys and values if either are strings)
  */
object DataGenRoute {

  final case class DataGenRequest(body: String)

  object DataGenRequest {
    given codec: Codec[DataGenRequest] = io.circe.generic.semiauto.deriveCodec[DataGenRequest]
  }

  enum DataGenResponse:
    case ParsedResponse(parsedInput: String, jsonResult: Option[Json])
    case TopicResponse(keyJson: Json, valueJson: Json)
  end DataGenResponse

  object DataGenResponse {
    object decoder extends Decoder[DataGenResponse] {
      def asTopicResponse(cursor: HCursor) = {
        for {
          keyJson <- cursor.downField("keyJson").as[Json]
          valueJson <- cursor.downField("valueJson").as[Json]
        } yield DataGenResponse.TopicResponse(keyJson, valueJson)
      }

      def asParsedResponse(cursor: HCursor) = {
        for {
          parsedInput <- cursor.downField("parsedInput").as[String]
          jsonResult <- cursor.downField("jsonResult").as[Option[Json]]
        } yield DataGenResponse.ParsedResponse(parsedInput, jsonResult)
      }

      override def apply(cursor: HCursor) = asTopicResponse(cursor).orElse(asTopicResponse(cursor))
    }

    object encoder extends Encoder[DataGenResponse] {
      override def apply(r: DataGenResponse) = r match {
        case ParsedResponse(parsedInput, json) =>
          Json.obj("parsedInput" -> parsedInput.asJson, "jsonResult" -> json.asJson)
        case TopicResponse(key, value) => Json.obj("key" -> key, "value" -> value)
      }
    }

    given code: Codec[DataGenResponse] = Codec.from(decoder, encoder)
  }


  def fromFranzConfig(franzConfig: FranzConfig): ZIO[Clock with Blocking, Nothing, HttpRoutes[Task]] = {

    for {
      env <- ZIO.environment[Clock with Blocking]
    } yield {
      //val publisher: PostRecord => UIO[Int] = KafkaPublishService(franzConfig).andThen(_.provide(env))
      parseUpload() <+> dataGenPost()
    }
  }

  def parseUpload(): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@(POST -> Root / "data" / "parse" :? OptionalSeed(seedOpt)) =>
        val seed = seedOpt.getOrElse(System.currentTimeMillis())
        parseAsMultipartRequest(req, seed).sandbox.either.flatMap {
          case Left(_) => parseAsRestRequest(req, seed)
          case Right(result) if result.status.code != 200 => parseAsRestRequest(req, seed)
          case Right(result) => UIO(result)
        }
    }
  }

  def dataGenPost(): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@(POST -> Root / "data" / "gen") =>
        parseAsMultipartRequest(req, 1).orElse(parseAsRestRequest(req, 1))
    }
  }

  def dataGenGet(): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@(GET -> Root / "data" / "gen" / topic) =>

        ???
    }
  }

  def parseAsRestRequest(req: Request[zio.Task], seed: Long) = {
    val content = req.as[Json].map(_.noSpaces).orElse(req.bodyText.foldMonoid.compile.string)
    content.flatMap(parseContentAsJson(_, seed)).map { parsed =>
      Response[Task](Status.Ok).withEntity(parsed)
    }
  }

  def parseContentAsJson(content: String, seed: Long): Task[Json] = {
    val asAvroJson: Task[Json] = Task.fromTry(DataGen.parseAvro(content).flatMap { schema =>
      Try(DataGen.forSchema(schema, seed))
    })

    val jsonFromHocon: Task[Json] = Task {
      val jasonString = ConfigFactory.parseString(content).root().render(ConfigRenderOptions.concise())
      io.circe.parser.parse(jasonString).toTry.get
    }

    val justAsJson: Task[Json] = Task(io.circe.parser.parse(content).toTry.get)

    asAvroJson.either.flatMap {
      case Left(_) =>
        jsonFromHocon.either.flatMap {
          case Left(_) =>
            justAsJson.orElse {
              ZIO.fail(new IllegalArgumentException(s"Couldn't parse content as avro, hocon or json: >${content}<"))
            }
          case Right(result) => UIO(result)
        }
      case Right(result) => UIO(result)
    }
  }

  def parseAsMultipartRequest(req: Request[zio.Task], seed: Long) = {
    req.decode[Multipart[Task]] { m => {
      val filePart = m.parts.find(_.name == Some("file"))
      filePart match {
        case None =>
          val msg = m.parts.flatMap(_.name).mkString("Multipart request did not contain 'file'. Parts included : [", ",", "]")
          BadRequest(msg)
        case Some(part) =>
          val catsIO = part.body.through(fs2.text.utf8Decode[Task]).foldMonoid.compile.string
          catsIO.flatMap { c =>
            LoggerFactory.getLogger(getClass).info(s"Parsing multipart content:>${c}<")
            parseContentAsJson(c, seed).map { parsed =>
              LoggerFactory.getLogger(getClass).info(s"Returning ${parsed}")
              Response[Task](Status.Ok).withEntity(parsed)
            }
          }
      }
    }
    }
  }

  def handleAsRestRequest(req: Request[zio.Task]) = {
    req.as[DataGenRequest].flatMap { request =>
      ???
    }
  }

}
