package expressions.rest.server

import cats.implicits.*
import com.typesafe.config.{Config, ConfigFactory}
import expressions.StringTemplate.StringExpression
import expressions.client.HttpRequest
import expressions.franz.FranzConfig
import expressions.rest.server.kafka.*
import expressions.{Cache, CodeTemplate, DynamicJson, StringTemplate}
import io.circe.Json
import io.circe.syntax.EncoderOps
import org.http4s
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.interop.catz.*
import zio.{Task, ZEnv, ZIO}

/**
  * You've got to have a little fun.
  */
object RestRoutes {
  type Resp = http4s.Response[Task]

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]

  val ScriptPrefix =
    """
      |import io.circe.syntax._
      |import io.circe.Json
      |import expressions.client._
      |import scalikejdbc._
      |
      |""".stripMargin

  def apply(defaultConfig: Config = ConfigFactory.load()) : ZIO[ZEnv, Throwable, HttpRoutes[Task]] = {

    for {
      env <- ZIO.environment[ZEnv]
      route <- forEnv(defaultConfig, env)
    } yield route
  }

  def forEnv(defaultConfig: Config, env: ZEnv): ZIO[ZEnv, Throwable, HttpRoutes[Task]] = {
    given implicitEnv: ZEnv = env

    for {
      cacheRoute <- CacheRoute()
      diskService <- Disk(defaultConfig)
      fsDir = BatchContext.dataDir(defaultConfig)
      httpCompiler = CodeTemplate.newCache[JsonMsg, Seq[HttpRequest]](ScriptPrefix)
      batchSink <- BatchSink.make
      franzConfig = FranzConfig(defaultConfig.getConfig("app.franz"))
      kafkaPublishRoute <- KafkaPublishRoute.fromFranzConfig(franzConfig)
      dataRoute = DataGenRoute()
    } yield {
      val expressionForString: Cache[StringExpression[JsonMsg]] =
        StringTemplate.newCache[DynamicJson, DynamicJson]()

      val diskRoute = DiskRoute(diskService)
      val loadCfg = LoadConfig(diskService, defaultConfig)
      val jsonCache: Cache[CodeTemplate.Expression[JsonMsg, Json]] = httpCompiler.map(_.andThen(_.asJson))
      val mappingRoutes = MappingTestRoute(jsonCache, _.asContext(fsDir))
      val configTestRotes = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute = ConfigRoute(diskService, defaultConfig)
      val batchRoute = BatchRoute(loadCfg, batchSink, env)
      val proxyRoute = ProxyRoute()

      batchRoute <+> kafkaPublishRoute <+> mappingRoutes <+> configTestRotes <+> dataRoute <+> configRoute <+> cacheRoute <+> diskRoute <+> proxyRoute
    }
  }
}
