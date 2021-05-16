package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import zio.Task

case class LoadConfig(disk  :Disk.Service, rootConfig : Config) {

  def at(path : List[String]) = {
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
    cfg.withOnlyPath("app")
  }

}
