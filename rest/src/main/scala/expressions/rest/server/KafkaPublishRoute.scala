package expressions.rest.server

import cats.implicits.toSemigroupKOps
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.client.kafka.PostRecord
import expressions.franz.FranzConfig
import expressions.rest.server.RestRoutes.taskDsl._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.blocking.Blocking
import zio.interop.catz._
import zio.{Task, UIO, ZIO}

object KafkaPublishRoute {

  def apply(rootConfig: Config = ConfigFactory.load()): ZIO[Blocking, Nothing, HttpRoutes[Task]] = fromFranzConfig(rootConfig.getConfig("app.franz"))

  def fromFranzConfig(franzConfig: Config): ZIO[Blocking, Nothing, HttpRoutes[Task]] = {
    ZIO.environment[Blocking].map { blocking =>
      val publisher: PostRecord => UIO[Int] = KafkaPublishService(FranzConfig(franzConfig)).andThen(_.provide(blocking))
      publish(publisher) <+> getDefault(franzConfig)
    }
  }

  //, defaultConfig: Config = ConfigFactory.load()
  def publish(handle: PostRecord => UIO[Int]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ (POST -> Root / "kafka" / "publish") => {
        for {
          postRequest <- req.as[PostRecord]
          result      <- handle(postRequest)
        } yield Response[Task](Status.Ok).withEntity(result)
      }
    }
  }

  def getDefault(franzConfig: Config): HttpRoutes[Task] = {
    val configJson = franzConfig.root.render(ConfigRenderOptions.concise())
    getDefault(PostRecord(TestData.asJson(TestData.testRecord()), config = configJson))
  }

  def getDefault(default: PostRecord): HttpRoutes[Task] = {
    val response = UIO(Response[Task](Status.Ok).withEntity(default))
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "publish" => response
    }
  }

}
