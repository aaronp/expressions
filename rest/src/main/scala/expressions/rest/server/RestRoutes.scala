package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.StringTemplate.StringExpression
import expressions.client.HttpRequest
import expressions.{Cache, JsonTemplate, RichDynamicJson, StringTemplate}
import org.http4s
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.interop.catz._

/**
  * You've got to have a little fun.
  */
object RestRoutes extends StrictLogging {

  type Resp = http4s.Response[Task]

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]

  def apply(defaultConfig: Config = ConfigFactory.load()) = {

    for {
      cacheRoute    <- CacheRoute()
      disk          <- DiskRoute(defaultConfig)
      fsDir         = KafkaRecordToHttpRequest.dataDir(defaultConfig)
      templateCache = JsonTemplate.newCache[HttpRequest]("import expressions.client._")
      kafkaRunner   <- KafkaSink(templateCache)
      kafkaPublishRoute <- KafkaPublishRoute(defaultConfig)
    } yield {
      val expressionForString: Cache[StringExpression[RichDynamicJson]] = StringTemplate.newCache[RichDynamicJson]("implicit val _implicitJsonValue = record.value.jsonValue")

      val mappingRoutes     = MappingTestRoute(templateCache, _.asContext(fsDir))
      val configTestRotes   = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute       = ConfigRoute(defaultConfig)
      val kafkaRoute        = KafkaRoute(kafkaRunner)

      kafkaRoute <+> kafkaPublishRoute <+> mappingRoutes <+> configTestRotes <+> configRoute <+> cacheRoute <+> disk
    }
  }
}
