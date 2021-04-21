package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Response, Status}
import zio.Task
import zio.interop.catz._

import scala.util.{Failure, Success, Try}

/**
  * Functions which can help the UI determine what a typesafe config contains
  */
object ConfigRoute {

  import RestRoutes.taskDsl._

  def apply(disk: Disk.Service, rootConfig: Config = ConfigFactory.load()): HttpRoutes[Task] = {
    listMappingsRoute(rootConfig) <+>
      getConfig(rootConfig, disk) <+>
      listEntries(rootConfig) <+>
      formatJson(rootConfig) <+>
      summary(rootConfig) <+>
      saveConfig(disk)
  }

  /**
    * Return a list of mappings for a given configuration
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

  /** enumerate the config entries with their comments, origin, key, value
    *
    * @param rootConfig
    * @return the default config
    */
  def listEntries(rootConfig: Config): HttpRoutes[Task] = {
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
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "format" =>
        for {
          body   <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
          lines  = formatConfigAsJson(config.withOnlyPath("app"))
        } yield Response[Task](Status.Ok).withEntity(lines)
    }
  }

  object OptionalIncludeDefault extends OptionalQueryParamDecoderMatcher[Boolean]("fallback")

  /**
    * @param rootConfig the root configuration
    * @return the default config
    */
  def summary(rootConfig: Config): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "parse" :? OptionalIncludeDefault(includeDefault) =>
        def parse(body: String) = {
          val withFallback = includeDefault.getOrElse(true)
          if (withFallback) {
            ConfigFactory.parseString(body).withFallback(rootConfig)
          } else {
            ConfigFactory.parseString(body)
          }
        }

        for {
          body    <- req.bodyText.compile.string
          config  <- Task(parse(body).resolve())
          summary = ConfigSummary.fromRootConfig(config)
        } yield Response[Task](Status.Ok).withEntity(summary)
    }
  }

  def saveConfig(disk: Disk.Service): HttpRoutes[Task] = {
    saveConfigRoute {
      case (fileName, summary: ConfigSummary) =>
        //        val jason = summary.asConfig().root().render(ConfigRenderOptions.concise())
        val jason = summary.asJson.noSpaces
        disk.write(List("config", fileName), jason).unit
    }
  }

  /**
    * @param saveConfig the function to save a configuration
    * @return the default config
    */
  def saveConfigRoute(saveConfig: (String, ConfigSummary) => Task[Unit]): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req @ POST -> Root / "config" / "save" / name =>
        for {
          summary                         <- req.as[ConfigSummary]
          result: Either[Throwable, Unit] <- saveConfig(name, summary).either
        } yield {
          result match {
            case Left(error) =>
              Response[Task](Status.Ok).withEntity(
                Map(
                  "error"   -> error.getLocalizedMessage.asJson,
                  "success" -> false.asJson,
                ))
            case Right(_) =>
              Response[Task](Status.Ok).withEntity(Map("success" -> true.asJson))
          }
        }
    }
  }

  /**
    * Should configs be returned as ConfigSummary json or configuration json?
    */
  object AsSummaryFormat extends OptionalQueryParamDecoderMatcher[Boolean]("summary")

  /**
    * @param rootConfig
    * @return the default config
    */
  def getConfig(rootConfig: Config, disk: Disk.Service): HttpRoutes[Task] = {
    def ok(jason: Json) = Response[Task](Status.Ok).withEntity(jason)

    def loadCfg(cfg: Config, asSummary: Boolean) = Task {
      if (asSummary) {
        ok(ConfigSummary.fromRootConfig(cfg).asJson)
      } else {
        val franzConf   = cfg.withOnlyPath("app.franz")
        val mappingConf = cfg.withOnlyPath("app.mapping")
        val jsonStr     = mappingConf.withFallback(franzConf).root.render(ConfigRenderOptions.concise())
        ok(parse(jsonStr).toTry.get)
      }
    }

    HttpRoutes.of[Task] {
      case (GET -> "config" /: theRest) :? AsSummaryFormat(asSummaryOpt) =>
        val asSummary = asSummaryOpt.getOrElse(false)
        theRest.toList match {
          case Nil => loadCfg(rootConfig, asSummary)
          case path =>
            disk.read("config" +: path).flatMap {
              case None if asSummary => Task(ok(ConfigSummary.empty.asJson))
              case None              => Task(Response[Task](Status.Ok).withEntity("{}"))
              case Some(found)       =>
                // try and read as a config summary first
                val parsed: Try[ConfigSummary] = io.circe.parser.parse(found).toTry.flatMap(_.as[ConfigSummary].toTry)

                parsed match {
                  case Success(summary) if asSummary => Task(ok(summary.asJson))
                  case Success(summary)              => loadCfg(summary.asConfig(), asSummary)
                  case Failure(_)                    => loadCfg(ConfigFactory.parseString(found), asSummary)
                }
            }
        }
    }
  }
}
