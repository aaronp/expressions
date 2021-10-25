package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import expressions.StringTemplate.StringExpression
import expressions.client.HttpRequest
import expressions.rest.server.kafka._
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
object RestRoutes {
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
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
      env          <- ZIO.environment[ZEnv]
      cacheRoute   <- CacheRoute()
      diskService  <- Disk(defaultConfig)
      fsDir        = kafka.KafkaRecordToHttpRequest.dataDir(defaultConfig)
      httpCompiler = CodeTemplate.newCache[JsonMsg, Seq[HttpRequest]](ScriptPrefix)
//      kafkaSink         <- KafkaSink(httpCompiler)
      batchSink         <- BatchSink.make
      kafkaPublishRoute <- KafkaPublishRoute(defaultConfig)
    } yield {
      val expressionForString: Cache[StringExpression[JsonMsg]] =
        StringTemplate.newCache[DynamicJson, DynamicJson]()

      val diskRoute                                                = DiskRoute(diskService)
      val loadCfg                                                  = LoadConfig(diskService, defaultConfig)
      val jsonCache: Cache[CodeTemplate.Expression[JsonMsg, Json]] = httpCompiler.map(_.andThen(_.asJson))
      val mappingRoutes                                            = MappingTestRoute(jsonCache, _.asContext(fsDir))
      val configTestRotes                                          = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute                                              = ConfigRoute(diskService, defaultConfig)
      val batchRoute                                               = BatchRoute(loadCfg, batchSink, env)
//      val kafkaRoute                                               = KafkaRoute(loadCfg, kafkaSink)
      val proxyRoute = ProxyRoute()

      //kafkaRoute <+>
      batchRoute <+> kafkaPublishRoute <+> mappingRoutes <+> configTestRotes <+> configRoute <+> cacheRoute <+> diskRoute <+> proxyRoute
    }
  }
}
