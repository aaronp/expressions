package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
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
    val easy = MappingTestRoute() <+> ConfigTestRoute()

    for {
      cacheRoute <- CacheRoute()
      disk       <- DiskRoute(defaultConfig)
    } yield {
      easy <+> cacheRoute <+> disk
    }
  }

}
