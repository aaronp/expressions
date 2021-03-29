package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import expressions.Unquote
import zio.ZIO
import zio.console.Console

import java.io.FileNotFoundException
import scala.util.{Failure, Success, Try}

/**
  * We have (separate to the kafka config) a list of:
  *
  * {{{
  *   app.mapping {
  *      <some topic name/regex> : "path/to/a/file.sc"
  *   }
  * }}}
  *
  *
  * Why do we do this?
  *
  * We have a 'app.franz.kafka` config which is just vanilla "this is how we get data from kafka into a black-box sink Task"
  *
  * One way we want to fill in that "black-box" sink task is with something which:
  *
  * {{{
  *  1) loads and compiles our `app.mapping` config so that the records (e.g. Context[Message]) can become HttpRequests
  *  2) invokes a RestClient w/ the resulting HttpRequests
  * }}}
  *
  * Risk/To Test:
  * $ we can keep retrying (and eventually blow up) for a given Request
  *
  * @param rootConfig
  */
case class MappingConfig(rootConfig: Config = ConfigFactory.load()) {

  val mappingConfig = rootConfig.getConfig("app.mapping")

  import args4c.implicits._

  /**
    * @return an association between a topic pattern (e.g. "foo*") to the path to the mapping file
    */
  val mappings: Seq[(String, List[String])] = mappingConfig.summaryEntries((_, v) => v).map { entry =>
    entry.key -> Unquote(entry.value.trim).split("/", -1).map(_.trim).toList
  }

  val (pathsByName, pathsByRegex) = {
    val (hasRegex, hasName) = mappings.partition(_._1.contains("*"))
    val fixed = hasName.toMap.ensuring(_.size == hasName.size).map {
      case (topic, path) => (topic, path)
    }
    val regexMap = hasRegex.map {
      case (regex, value) => (Unquote(regex.replace("*", ".*")).r, value)
    }
    fixed -> regexMap
  }

  /**
    * Find the path (e.g. as a list of segments) to the mapping script for a particular topic
    * @param topic the topic to look up
    * @return the path (e.g. path/to/script.sc is [path, to, script.sc]) for the mapping
    */
  def lookup(topicIn: String): Option[List[String]] = {
    val topic = MappingConfig.unquote(topicIn)
    pathsByName.get(topic).orElse {
      pathsByRegex.collectFirst {
        case (regex, value) if regex.matches(topic) => value
      }
    }
  }

  def scriptForTopic(disk: Disk.Service): ZIO[Console, Throwable, Topic => Try[String]] = {
    for {
      scriptByPathSeq <- ZIO.foreach(mappings) {
        case (topic, path) =>
          disk.read(path).flatMap {
            case None         => ZIO.fail(new FileNotFoundException(s"Couldn't read ${path.mkString("/")} as specified by topic '${topic}'"))
            case Some(script) => ZIO.succeed(path -> script)
          }
      }
      scriptByPath = scriptByPathSeq.toMap
    } yield { (topic: String) =>
      lookup(topic).flatMap(scriptByPath.get) match {
        case Some(path) => Success(path)
        case None       => Failure(new NoSuchElementException(s"No mapping found for '${topic}' in $this"))
      }
    }
  }
}

object MappingConfig {
  import args4c.implicits._
  def apply(config: String, theRest: String*): MappingConfig = MappingConfig((config +: theRest).toArray.asConfig())

  private def unquote(s: String): String = {
    s.trim match {
      case s""""${str}"""" => unquote(str)
      case str             => s
    }
  }
}
