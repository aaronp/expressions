package expressions.rest.server

import cats.implicits._
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import io.circe.Json
import io.circe.parser.parse
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._
import org.http4s.{HttpRoutes, Request, Response, Status}
import zio.interop.catz._
import zio.{Task, URIO, ZIO}

import scala.util.{Failure, Success, Try}

/**
  * Functions which can help the UI determine what a typesafe config contains
  */
object ConfigRoute {

  import RestRoutes.taskDsl._

  /**
    * We can read and write either Config or ConfigSummary instances.
    *
    * If saving a [[ConfigSummary]], we first try and read the config and merge it.
    * If saving a Config, we just save it.
    *
    * When reading a Config, we always read it as a jason typesafe config, then optionally turn
    * it into a ConfigSummary (dependending on ?summary=true)
    *
    * @param disk
    * @param rootConfig
    * @return
    */
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
      case req@POST -> Root / "config" / "mappings" / "list" =>
        for {
          body <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
        } yield {
          val mappings: Map[String, List[String]] = MappingConfig(config).mappings.toMap
          val json = mappings.asJson
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
      case req@POST -> Root / "config" / "entries" =>
        for {
          body <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
          lines = ConfigLine(config.withOnlyPath("app"))
        } yield Response[Task](Status.Ok).withEntity(lines)
    }
  }

  def formatConfigAsJson(config: Config): List[String] = {
    val jsonString = config.root.render(ConfigRenderOptions.defaults.setComments(false).setOriginComments(false))
    jsonString.linesIterator.toList
  }

  def formatJson(rootConfig: Config): HttpRoutes[Task] = {
    HttpRoutes.of[Task] {
      case req@POST -> Root / "config" / "format" =>
        for {
          body <- req.bodyText.compile.string
          config <- Task(ConfigFactory.parseString(body).withFallback(rootConfig).resolve())
          lines = formatConfigAsJson(config.withOnlyPath("app"))
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
      case req@POST -> Root / "config" / "parse" :? OptionalIncludeDefault(includeDefault) =>
        def parse(body: String) = {
          val withFallback = includeDefault.getOrElse(true)
          if (withFallback) {
            ConfigFactory.parseString(body).withFallback(rootConfig)
          } else {
            ConfigFactory.parseString(body)
          }
        }

        for {
          body <- req.bodyText.compile.string
          config <- Task(parse(body).resolve())
          summary = ConfigSummary.fromRootConfig(config)
        } yield Response[Task](Status.Ok).withEntity(summary)
    }
  }

  private def asJson(config: Config) = {
    val jasonStr = config.root().render(ConfigRenderOptions.concise())
    ZIO.fromTry(io.circe.parser.parse(jasonStr).toTry)
  }

  private def merge(previousConfig: Option[String], config: Config) = {
    previousConfig match {
      case None => ZIO.succeed(config)
      case Some(str) =>
        val parsed = ZIO(ConfigFactory.parseString(str)).refineOrDie {
          case err => new Exception(s"Error '${err.getMessage}' parsing existing configuration: $str")
        }
        parsed
    }
  }

  /**
    * If saving a [[ConfigSummary]], we first try and read the config and merge it.
    * If saving a Config, we just save it.
    *
    * @param disk
    * @return the route
    */
  def saveConfig(disk: Disk.Service): HttpRoutes[Task] = {

    saveConfigRoute {
      // If saving a [[ConfigSummary]], we first try and read the config and merge it.
      case (fileName, Right(rawConfig)) =>
        val path = List("config", fileName)
        for {
          previousConfigStrOpt <- disk.read(path)
          config <- merge(previousConfigStrOpt, rawConfig)
          jason <- asJson(config)
          _ <- disk.write(path, jason.noSpaces)
        } yield ()
      case (fileName, Left(summary)) =>

        //        val jason = summary.asConfig().root().render(ConfigRenderOptions.concise())
        //        disk.write(List("config", fileName), jason) >>>
        disk.write(List("config", fileName), summary.asJson.noSpaces).unit
    }
  }

  /**
    * @param saveConfig the function to save a configuration
    * @return the default config
    */
  def saveConfigRoute(saveConfig: (String, Either[ConfigSummary, Config]) => Task[Unit]): HttpRoutes[Task] = {

    def asResponse(result: Either[Throwable, Unit]) = {
      result match {
        case Left(error) =>
          Response[Task](Status.Ok).withEntity(
            Map(
              "error" -> error.getLocalizedMessage.asJson,
              "success" -> false.asJson,
            ))
        case Right(_) =>
          Response[Task](Status.Ok).withEntity(Map("success" -> true.asJson))
      }
    }

    def saveAsSummary(req: Request[Task], name: String): ZIO[Any, Throwable, Response[Task]#Self] = {
      for {
        summary <- req.as[ConfigSummary]
        result <- saveConfig(name, Left(summary)).either
      } yield asResponse(result)
    }

    def saveAsRawConfig(req: Request[Task], name: String): ZIO[Any, Throwable, Response[Task]#Self] = {
      req.as[String].flatMap { configText =>
        Try(ConfigFactory.parseString(configText)) match {
          case Failure(err) =>
            ZIO.succeed(asResponse(Left(err)))
          case Success(config) =>
            saveConfig(name, Right(config)).either.map(asResponse)
        }
      }

    }

    HttpRoutes.of[Task] {
      case req@POST -> Root / "config" / "save" / name =>
        saveAsSummary(req, name).catchAll {
          case _ => saveAsRawConfig(req, name)
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
        val franzConf = cfg.withOnlyPath("app.franz")
        val mappingConf = cfg.withOnlyPath("app.mapping")
        val jsonStr = mappingConf.withFallback(franzConf).root.render(ConfigRenderOptions.concise())
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
              case None => Task(Response[Task](Status.Ok).withEntity(Json.obj()))
              case Some(found) =>
                // try and read as a config summary first
//                val parsed: Try[ConfigSummary] = io.circe.parser.parse(found).toTry.flatMap(_.as[ConfigSummary].toTry)
//
//                parsed match {
//                  case Success(summary) if asSummary => Task(ok(summary.asJson))
//                  case Success(summary) => loadCfg(summary.asConfig(), asSummary)
//                  case Failure(_) => loadCfg(ConfigFactory.parseString(found), asSummary)
//                }
                loadCfg(ConfigFactory.parseString(found), asSummary)
            }
        }
    }
  }
}
