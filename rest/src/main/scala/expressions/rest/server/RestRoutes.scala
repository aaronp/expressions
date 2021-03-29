package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.StringTemplate.StringExpression
import expressions.client.HttpRequest
import expressions.rest.server.kafka.{BatchSink, KafkaPublishRoute, KafkaRoute, KafkaSink, BatchRoute}
import expressions.{Cache, CodeTemplate, DynamicJson, StringTemplate}
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

  val ScriptPrefix =
    """
      |import io.circe.syntax._
      |import io.circe.Json
      |import expressions.client._
      |""".stripMargin

  def apply(defaultConfig: Config = ConfigFactory.load()): ZIO[ZEnv, Throwable, HttpRoutes[Task]] = {
    for {
      env               <- ZIO.environment[ZEnv]
      cacheRoute        <- CacheRoute()
      disk              <- DiskRoute(defaultConfig)
      fsDir             = kafka.KafkaRecordToHttpRequest.dataDir(defaultConfig)
      templateCache     = CodeTemplate.newCache[JsonMsg, Seq[HttpRequest]](ScriptPrefix)
      kafkaSink         <- KafkaSink(templateCache)
      batchSink         <- BatchSink.make
      kafkaPublishRoute <- KafkaPublishRoute(defaultConfig)
    } yield {
      val expressionForString: Cache[StringExpression[JsonMsg]] =
        StringTemplate.newCache[DynamicJson, DynamicJson]()

      val jsonCache: Cache[CodeTemplate.Expression[JsonMsg, Json]] = templateCache.map(_.andThen(_.asJson))
      val mappingRoutes                                            = MappingTestRoute(jsonCache, _.asContext(fsDir))
      val configTestRotes                                          = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute                                              = ConfigRoute(defaultConfig)
      val batchRoute                                               = BatchRoute(defaultConfig, batchSink, env)
      val kafkaRoute                                               = KafkaRoute(defaultConfig, kafkaSink)
      val proxyRoute                                               = ProxyRoute()

      batchRoute <+> kafkaRoute <+> kafkaPublishRoute <+> mappingRoutes <+> configTestRotes <+> configRoute <+> cacheRoute <+> disk <+> proxyRoute
    }
  }
}
