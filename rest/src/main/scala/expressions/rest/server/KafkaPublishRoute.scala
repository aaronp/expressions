package expressions.rest.server

import cats.implicits.toSemigroupKOps
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.client.kafka.PostRecord
import expressions.franz.FranzConfig
import expressions.rest.server.RestRoutes.taskDsl._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.interop.catz._
import zio.{Task, UIO}

object KafkaPublishRoute {

  def apply(rootConfig: Config = ConfigFactory.load()) : HttpRoutes[Task] = fromFranzConfig(rootConfig.getConfig("app.franz"))

  def fromFranzConfig(franzConfig: Config): HttpRoutes[Task] = {
    val publisher = KafkaPublishService(FranzConfig(franzConfig))
    publish(publisher) <+> getDefault(franzConfig)
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
    getDefault(PostRecord(configJson, TestData.asJson(TestData.testRecord())))
  }

  def getDefault(default: PostRecord): HttpRoutes[Task] = {
    val response = UIO(Response[Task](Status.Ok).withEntity(default))
    HttpRoutes.of[Task] {
      case GET -> Root / "kafka" / "publish" => response
    }
  }

}
