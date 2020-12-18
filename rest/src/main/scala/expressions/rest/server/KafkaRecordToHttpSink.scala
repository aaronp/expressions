package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import expressions.JsonTemplate.Expression
import expressions.client.{HttpRequest, RestClient}
import expressions.franz.KafkaRecord
import expressions.template.{Context, Message}
import expressions.{Cache, JsonTemplate, RichDynamicJson}
import io.circe.Json
import sttp.client.Response
import zio.console.Console
import zio.{Task, ZIO}

import scala.util.Try

/**
  * KafkaRecordToHttpSink - the guts of the logic hole for mapping/writing KafkaRecords to some http sink
  *
  * @param mappingConfig
  * @param templateCache
  * @param scriptForTopic
  * @param asContext
  * @tparam A
  * @tparam B
  */
// format: off
case class KafkaRecordToHttpSink[A, B](mappingConfig: MappingConfig,
                                       templateCache: Cache[Expression[A, B]],
                                       scriptForTopic: String => Try[String],
                                       asContext: KafkaRecord => Context[A]) {
// format: on

  /**
    * @param topic the topic for which there is a mapping function
    * @return the Context[A] => B function for the given topic
    */
  def mappingForTopic(topic: String) = scriptForTopic(topic).flatMap(templateCache.apply)

  def makeRestRequest(record: KafkaRecord)(implicit outputAsRequest: B =:= HttpRequest): ZIO[Any, Throwable, Response[Either[String, String]]] = {
    asRestRequest(record).map(RestClient.send)
  }

  def asRestRequest(record: KafkaRecord)(implicit outputAsRequest: B =:= HttpRequest): Task[HttpRequest] = {
    for {
      asRequest <- Task.fromTry(mappingForTopic(record.topic))
      request   = outputAsRequest(asRequest(asContext(record)))
    } yield request
  }
}
object KafkaRecordToHttpSink {

  import eie.io._

  def dataDir(rootConfig: Config) = rootConfig.getString("app.data").asPath

  def apply(rootConfig: Config = ConfigFactory.load(),
            templateCache: Cache[Expression[RichDynamicJson, HttpRequest]] = JsonTemplate.newCache[HttpRequest]()): ZIO[Console, Throwable, KafkaRecordToHttpSink[RichDynamicJson, HttpRequest]] = {
    val mappingConfig: MappingConfig = MappingConfig(rootConfig)
    for {
      disk <- Disk(rootConfig)
      inst <- apply(mappingConfig, disk, templateCache) { record =>
        record.asContext(dataDir(rootConfig))
      }
    } yield inst
  }

  def apply(mappingConfig: MappingConfig, disk: Disk.Service, templateCache: Cache[Expression[RichDynamicJson, HttpRequest]])(
      asContext: Message[RichDynamicJson] => Context[RichDynamicJson]): ZIO[Console, Throwable, KafkaRecordToHttpSink[RichDynamicJson, HttpRequest]] = {
    mappingConfig.scriptForTopic(disk).map { lookup =>
      KafkaRecordToHttpSink(mappingConfig, templateCache, lookup, (asMessage _).andThen(asContext))
    }
  }

  def asMessage(record: KafkaRecord): Message[RichDynamicJson] = {
    Message(new RichDynamicJson(record.recordJson), record.key, record.timestamp, record.headers)
  }

  def writeScriptForTopic(mappingConfig: MappingConfig, disk: Disk.Service, topic: String, script: String): ZIO[Any, Serializable, Unit] = {
    for {
      pathToMapping <- ZIO.fromOption(mappingConfig.lookup(topic))
      _             <- disk.write(pathToMapping, script)
    } yield ()
  }
}
