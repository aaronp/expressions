package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.JsonTemplate.Expression
import expressions.StringTemplate.StringExpression
import expressions.{Cache, JsonTemplate, RichDynamicJson, StringTemplate}
import io.circe.Json
import org.http4s
import org.http4s.dsl.Http4sDsl
import zio.{Task, ZEnv, ZIO}
import zio.interop.catz._

/**
  * You've got to have a little fun.
  */
object RestRoutes extends StrictLogging {

  type Resp = http4s.Response[Task]

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]

  def apply(defaultConfig: Config = ConfigFactory.load()) = {

    for {
      cacheRoute                                              <- CacheRoute()
      disk                                                    <- DiskRoute(defaultConfig)
      fsDir                                                   = KafkaRecordToHttpSink.dataDir(defaultConfig)
      templateCache: Cache[Expression[RichDynamicJson, Json]] = JsonTemplate.newCache[Json]("import expressions.client._")
      kafkaRunner                                             <- BusinessLogic(templateCache)
    } yield {
      val expressionForString: Cache[StringExpression[RichDynamicJson]] = StringTemplate.newCache[RichDynamicJson]("implicit val _implicitJsonValue = record.value.jsonValue")

      val mappingRoutes   = MappingTestRoute(templateCache, _.asContext(fsDir))
      val configTestRotes = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute     = ConfigRoute(defaultConfig)
      val kafkaRoute      = KafkaRoute(kafkaRunner)

      kafkaRoute <+> mappingRoutes <+> configTestRotes <+> configRoute <+> cacheRoute <+> disk
    }
  }
}
