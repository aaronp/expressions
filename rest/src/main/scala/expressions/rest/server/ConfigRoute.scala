package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.Task
import zio.interop.catz._
import cats.implicits._
import io.circe.Json
import io.circe.parser.parse

/**
  * Functions which can help the UI determine what a typesafe config contains
  */
object ConfigRoute {

  import RestRoutes.taskDsl._

  def apply(rootConfig: Config = ConfigFactory.load()): HttpRoutes[Task] = {
    listMappingsRoute(rootConfig) <+> defaultConfig(rootConfig)
  }

  /**
    *
    * @return json representing the mapping paths by their topics/regex
    */
  def listMappingsRoute(rootConfig: Config): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "mappings" / "list" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
        } yield {
          val mappings: Map[String, List[String]] = MappingConfig(config).mappings.toMap
          val json                                = mappings.asJson
          Response[Task](Status.Ok).withEntity(json)
        }
    }
  }
  def defaultConfig(rootConfig: Config): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case GET -> Root / "config" =>
        Task {
          val franzConf   = rootConfig.withOnlyPath("app.franz")
          val mappingConf = rootConfig.withOnlyPath("app.mapping")
          val jsonStr     = mappingConf.withFallback(franzConf).root.render(ConfigRenderOptions.concise())
          val jason: Json = parse(jsonStr).toTry.get
          Response[Task](Status.Ok).withEntity(jason)
        }
    }
  }
}
