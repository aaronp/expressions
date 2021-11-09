package expressions.rest.server.kafka

import cats.implicits.*
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.client.kafka.PostRecord
import expressions.franz.{DataGen, FranzConfig, SchemaGen}
import expressions.rest.server.*
import expressions.rest.server.RestRoutes.taskDsl.*
import io.circe.syntax.EncoderOps
import io.circe.{Codec, Json}
import io.confluent.kafka.schemaregistry.client.{SchemaMetadata, SchemaRegistryClient}
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.{HttpRoutes, Response, Status}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.interop.catz.*
import zio.{Task, UIO, ZIO}

import scala.util.Try

object KafkaPublishRoute {

  object model {

    case class Topics(keys: List[String], values: List[String], other: List[String])

    object Topics {
      def apply(all: Iterable[String]): Topics = {
        val e = List[String]()
        val (keys, values, other) = all.foldLeft((e, e, e)) {
          case ((keys, values, other), s"${subject}-key") => (subject :: keys, values, other)
          case ((keys, values, other), s"${subject}-value") => (keys, subject :: values, other)
          case ((keys, values, other), topic) => (keys, values, topic :: other)
        }
        Topics(keys.sorted, values.sorted, other.sorted)
      }

      given codec: Codec[Topics] = io.circe.generic.semiauto.deriveCodec[Topics]
    }

    case class SubjectData(subject: String, version: Int, schema: Json, testData: Json)

    object SubjectData {
      def forSchema(seed :Long)(subject: String, schema: SchemaMetadata): Option[SubjectData] = {
        SchemaGen.parseSchema(schema.getSchema).toOption.flatMap { s =>
          val d8a = DataGen.forSchema(s, seed)
          io.circe.parser.parse(schema.getSchema).toOption.map { parsedSchema =>
            SubjectData(subject, schema.getVersion, parsedSchema, d8a)
          }
        }
      }

      given codec: Codec[SubjectData] = io.circe.generic.semiauto.deriveCodec[SubjectData]
    }

    case class TopicData(key: Option[SubjectData], value: Option[SubjectData], other: Option[SubjectData])

    object TopicData {
      type NamedSchema = (String, SchemaMetadata)

      def forTopic(client: SchemaRegistryClient, rawTopic :String, seed : Long) = {
        val StripTopic(topic) = rawTopic
        val keySubject = s"$topic-key"
        val valueSubject = s"$topic-value"
        for {
          keySchemaF <- Task(client.schemaMetadata(keySubject)).either.fork
          valueSchemaF <- Task(client.schemaMetadata(valueSubject)).either.fork
          vanilla <- Task(client.schemaMetadata(topic)).either
          key <- keySchemaF.join
          value <- valueSchemaF.join
          result = TopicData.forNamedSchemas(
            seed,
            key.toOption.map(keySubject -> _),
            value.toOption.map(valueSubject -> _),
            vanilla.toOption.map(topic -> _)
          )
        } yield result
      }

      def forNamedSchemas(seed : Long, keyOpt: Option[NamedSchema], valueOpt: Option[NamedSchema], otherOpt: Option[NamedSchema]): TopicData = {
        TopicData(
          keyOpt.flatMap(SubjectData.forSchema(seed)),
          valueOpt.flatMap(SubjectData.forSchema(seed)),
          otherOpt.flatMap(SubjectData.forSchema(seed))
        )
      }

      given codec: Codec[TopicData] = io.circe.generic.semiauto.deriveCodec[TopicData]
    }
  }
  import model.*

  object OptionalSeed extends OptionalQueryParamDecoderMatcher[Long]("seed")

  def apply(rootConfig: Config = ConfigFactory.load()): ZIO[Clock with Blocking, Nothing, HttpRoutes[Task]] =
    fromFranzConfig(FranzConfig(rootConfig.getConfig("app.franz")))

  def fromFranzConfig(franzConfig : FranzConfig): ZIO[Clock with Blocking, Nothing, HttpRoutes[Task]] = {
    for {
      env <- ZIO.environment[Clock with Blocking]
    } yield {
      val publisher: PostRecord => UIO[Int] = KafkaPublishService(franzConfig).andThen(_.provide(env))
      publish(publisher) <+> topicsGet(franzConfig) <+> topicsPost(franzConfig) <+> topic(franzConfig)
    }
  }

  def publish(handle: PostRecord => UIO[Int]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@(POST -> Root / "kafka" / "publish") =>
        val resultTask = for {
          postRequest <- req.as[PostRecord]
          result <- handle(postRequest)
        } yield Response[Task](Status.Ok).withEntity(result)

        resultTask.sandbox.either.map {
          case Left(err) => Response[Task](Status.InternalServerError).withEntity(s"Error: $err")
          case Right(result) => result
        }
    }
  }

  def topicsPost(defaultConfig: FranzConfig): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@(POST -> Root / "kafka" / "topics") =>
        for {
          configOverrides <- req.as[String]
          config = defaultConfig.withOverrides(configOverrides)
          all = config.schemaRegistryClient.subjects
        } yield Response[Task](Status.Ok).withEntity(Topics(all))
    }
  }

  def topicsGet(defaultConfig: FranzConfig): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "topics" =>
        for {
          all <- Task(defaultConfig.schemaRegistryClient.subjects)
        } yield Response[Task](Status.Ok).withEntity(Topics(all))
    }
  }

  def topic(defaultConfig: FranzConfig): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "topic" / topic :? OptionalSeed(seedOpt) =>
        val seed = seedOpt.getOrElse(defaultConfig.defaultSeed)
        for {
          result <- TopicData.forTopic(defaultConfig.schemaRegistryClient, topic, seed)
        } yield Response[Task](Status.Ok).withEntity(result)
    }
  }

}
