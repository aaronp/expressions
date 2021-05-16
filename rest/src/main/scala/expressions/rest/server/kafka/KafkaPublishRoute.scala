package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.client.kafka.PostRecord
import expressions.franz.FranzConfig
import io.circe.Json
import org.http4s.{HttpRoutes, Response, Status}
import zio.blocking.Blocking
import zio.{Task, UIO, ZIO}

import expressions.rest.server.RestRoutes.taskDsl.*
import zio.interop.catz.*
import org.http4s.circe.CirceEntityCodec.*
import cats.implicits.*
import io.circe.syntax.EncoderOps
import zio.clock.Clock

object KafkaPublishRoute {

  def apply(rootConfig: Config = ConfigFactory.load()): ZIO[Clock with Blocking, Nothing, HttpRoutes[Task]] = fromFranzConfig(rootConfig.getConfig("app.franz"))

  def fromFranzConfig(franzConfig: Config): ZIO[Clock with Blocking, Nothing, HttpRoutes[Task]] = {
    for {
      env <- ZIO.environment[Clock with Blocking]
    } yield {
      val publisher: PostRecord => UIO[Int] = KafkaPublishService(FranzConfig(franzConfig)).andThen(_.provide(env))
      publish(publisher) <+> getDefault(franzConfig)
    }
  }

  def publish(handle: PostRecord => UIO[Int]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ (POST -> Root / "kafka" / "publish") =>
        val resultTask = for {
          postRequest <- req.as[PostRecord]
          result      <- handle(postRequest)
        } yield Response[Task](Status.Ok).withEntity(result)

        resultTask.sandbox.either.map {
          case Left(err)     => Response[Task](Status.InternalServerError).withEntity(s"Error: $err")
          case Right(result) => result
        }
    }
  }

  def getDefault(franzConfig: Config): HttpRoutes[Task] = {
    val configJson = franzConfig.root.render(ConfigRenderOptions.concise())
    val example = (Json.obj(
      "example" -> Json.obj("nested" -> Json.obj("array" -> List(1, 2, 3).asJson)),
      "boolean" -> true.asJson,
      "number"  -> 123.asJson
    ))
    getDefault(PostRecord(example, config = configJson))
  }

  def getDefault(default: PostRecord): HttpRoutes[Task] = {
    val response = UIO(Response[Task](Status.Ok).withEntity(default))
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "publish" => response
    }
  }

}
