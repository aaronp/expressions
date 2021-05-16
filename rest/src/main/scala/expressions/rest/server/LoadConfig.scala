package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe.Json
import io.circe.parser.parse
import org.http4s.{Response, Status}
import zio.Task

case class LoadConfig(disk  :Disk.Service) {

  def apply(rootConfig : Config, path : List[String]) = {
    path match {
      case Nil => loadCfg(rootConfig)
      case path =>
        disk.read("config" +: path).flatMap {
          case None | Some("") if path == List("application.conf") => Task(rootConfig)
          case None => Task(ConfigFactory.empty)
          case Some(found) => loadCfg(ConfigFactory.parseString(found))
        }
    }
  }
  def loadCfg(cfg: Config) = Task {
    val franzConf = cfg.withOnlyPath("app.franz")
    val mappingConf = cfg.withOnlyPath("app.mapping")
    mappingConf.withFallback(franzConf)
  }

}
