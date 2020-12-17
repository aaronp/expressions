package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.StringTemplate.StringExpression
import expressions.{Cache, JsonTemplate, RichDynamicJson, StringTemplate}
import io.circe.Json
import org.http4s
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import zio.Task
import zio.interop.catz._

/**
  * You've got to have a little fun.
  */
object RestRoutes extends StrictLogging {

  type Resp = http4s.Response[Task]

  val taskDsl: Http4sDsl[Task] = Http4sDsl[Task]

  def apply(defaultConfig: Config = ConfigFactory.load()): Task[HttpRoutes[Task]] = {

    for {
      cacheRoute <- CacheRoute()
      disk       <- DiskRoute(defaultConfig)
    } yield {
      val expressionForString: Cache[StringExpression[RichDynamicJson]] = StringTemplate.newCache[RichDynamicJson]("implicit val _implicitJsonValue = record.value.jsonValue")

      val templateCache   = JsonTemplate.newCache[Json]("import expressions.client._")
      val fsDir           = KafkaRecordToHttpSink.dataDir(defaultConfig)
      val mappingRoutes   = MappingTestRoute(templateCache, _.asContext(fsDir))
      val configTestRotes = ConfigTestRoute(expressionForString, _.asContext(fsDir))
      val configRoute     = ConfigRoute(defaultConfig)

      mappingRoutes <+> configTestRotes <+> configRoute <+> cacheRoute <+> disk
    }
  }
}
