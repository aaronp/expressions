package pipelines.rest

import com.typesafe.config.Config
import pipelines.rest.routes.{SecureRouteSettings, StaticFileRoutes}
import pipelines.rest.routes.StaticFileRoutes.fromRootConfig

object DevConfig {

  import args4c.implicits._
  def apply(): Config = Array("dev.conf").asConfig(RestMain.defaultConfig()).resolve()

  def secureSettings(): SecureRouteSettings = SecureRouteSettings.fromRoot(apply())

  def staticRoutes(): StaticFileRoutes = fromRootConfig(apply())

}
