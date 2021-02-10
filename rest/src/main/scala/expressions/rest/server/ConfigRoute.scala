package expressions.rest.server

import args4c.StringEntry
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
    listMappingsRoute(rootConfig) <+>
      defaultConfig(rootConfig) <+>
      listEntries(rootConfig) <+>
      formatJson(rootConfig) <+>
      summary(rootConfig)
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
    * The parsed pieces from the typesafe config in a json-friendly data structure
    *
    * @param topic
    * @param brokers
    * @param mappings a mapping of the topic to
    * @param keyType
    * @param valueType
    */
  case class ConfigSummary(topic: String, brokers: List[String], mappings: Map[String, List[String]], keyType: String, valueType: String)
  object ConfigSummary {
    implicit val codec = io.circe.generic.semiauto.deriveCodec[ConfigSummary]
  }

  case class ConfigLine(comments: List[String], origin: String, key: String, value: String)
  object ConfigLine {
    implicit val codec = io.circe.generic.semiauto.deriveCodec[ConfigLine]
    def apply(config: Config): Seq[ConfigLine] = {
      import args4c.implicits._
      config.summaryEntries().map {
        case StringEntry(comments, origin, key, value) => ConfigLine(comments, origin, key, value)
      }
    }
  }

  /** enumerate the config entries with their comments, origin, key, value
    * @param rootConfig
    * @return the default config
    */
  def listEntries(rootConfig: Config): HttpRoutes[Task] = {
    import args4c.implicits._
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "entries" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
          lines  = ConfigLine(config.withOnlyPath("app"))
        } yield Response[Task](Status.Ok).withEntity(lines)
    }
  }

  def formatConfigAsJson(config: Config): List[String] = {
    val jsonString = config.root.render(ConfigRenderOptions.defaults.setComments(false).setOriginComments(false))
    jsonString.linesIterator.toList
  }

  def formatJson(rootConfig: Config): HttpRoutes[Task] = {
    import args4c.implicits._
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "format" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
          lines  = formatConfigAsJson(config.withOnlyPath("app"))
        } yield Response[Task](Status.Ok).withEntity(lines)
    }
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
          config   <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
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
      case _ -> Root / "config" =>
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
