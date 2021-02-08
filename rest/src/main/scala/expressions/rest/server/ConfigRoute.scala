package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import expressions.franz.FranzConfig
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.Task
import zio.interop.catz._

/**
  * Functions which can help the UI determine what a typesafe config contains
  */
object ConfigRoute {

  import RestRoutes.taskDsl._

  def apply(rootConfig: Config = ConfigFactory.load()): HttpRoutes[Task] = {
    listMappingsRoute(rootConfig) <+> defaultConfig(rootConfig) <+> summary(rootConfig)
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

  /**
    * The 'most interesting bits' from a config
    */
  case class ConfigSummary(topic: String, brokers: List[String], mappings: Map[String, List[String]], keyType: String, valueType: String)
  object ConfigSummary {
    implicit val codec = io.circe.generic.semiauto.deriveCodec[ConfigSummary]
  }

  /**
    * @param rootConfig
    * @return the default config
    */
  def summary(rootConfig: Config): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "parse" =>
        for {
          body     <- req.bodyText.compile.string
          _ = println(s"parsing config:\n$body\n")
          config   <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
          _ = println(s"parsed config:\n${config.getConfig("app").root().render()}\n")
          mappings = MappingConfig(config).mappings.toMap
          fc       = FranzConfig.fromRootConfig(config)
          summary  = ConfigSummary(topic = fc.topic, brokers = fc.consumerSettings.bootstrapServers, mappings, fc.keyType.name, fc.valueType.name)
        } yield Response[Task](Status.Ok).withEntity(summary)
    }
  }

  /**
    * @param rootConfig
    * @return the default config
    */
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
