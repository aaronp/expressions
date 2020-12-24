package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.StringTemplate.StringExpression
import expressions.client.HttpRequest
import expressions.{Cache, JsonTemplate, RichDynamicJson, StringTemplate}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.interop.catz._
import zio.{Task, ZEnv, ZIO}

/**
  * You've got to have a little fun.
  */
object RestRoutes extends StrictLogging {

  type Resp = http4s.Response[Task]

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]

  def apply(defaultConfig: Config = ConfigFactory.load()): ZIO[ZEnv, Throwable, HttpRoutes[Task]] = {

    for {
      cacheRoute        <- CacheRoute()
      disk              <- DiskRoute(defaultConfig)
      fsDir             = KafkaRecordToHttpRequest.dataDir(defaultConfig)
      templateCache     = JsonTemplate.newCache[JsonMsg, List[HttpRequest]]("import expressions.client._")
      kafkaSink         <- KafkaSink(templateCache)
      kafkaPublishRoute <- KafkaPublishRoute(defaultConfig)
    } yield {
      val expressionForString: Cache[StringExpression[JsonMsg]] =
        StringTemplate.newCache[RichDynamicJson, RichDynamicJson]("implicit val _implicitJsonValue = record.value.jsonValue")

      val jsonCache: Cache[JsonTemplate.Expression[JsonMsg, Json]] = templateCache.map(_.andThen(_.asJson))
      val mappingRoutes                                            = MappingTestRoute(jsonCache, _.asContext(fsDir))
      val configTestRotes                                          = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute                                              = ConfigRoute(defaultConfig)
      val kafkaRoute                                               = KafkaRoute(kafkaSink)
      val proxyRoute                                               = ProxyRoute()

      kafkaRoute <+> kafkaPublishRoute <+> mappingRoutes <+> configTestRotes <+> configRoute <+> cacheRoute <+> disk <+> proxyRoute
    }
  }
}
