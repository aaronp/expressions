package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import expressions.JsonTemplate.Expression
import expressions.client.{HttpRequest, RestClient}
import expressions.franz.KafkaRecord
import expressions.template.{Context, Message}
import expressions.{Cache, JsonTemplate, RichDynamicJson}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
import sttp.client.Response
import zio.console.Console
import zio.kafka.consumer.CommittableRecord
import zio.{Task, ZIO}

import java.nio.file.Path
import scala.util.Try

/**
  * KafkaRecordToHttpRequest - a function which can map a [[KafkaRecord]] as [[HttpRequest]](s)
  *
  * @param mappingConfig
  * @param templateCache
  * @param scriptForTopic
  * @param asContext
  * @tparam A the input context type (e.g.
  * @tparam B
  */
// format: off
case class KafkaRecordToHttpRequest[K, V, B](mappingConfig: MappingConfig,
                                          templateCache: Cache[Expression[JsonMsg, B]],
                                          scriptForTopic: String => Try[String],
                                          asContext: CommittableRecord[K,V] => Context[JsonMsg]) {
// format: on

  /**
    * @param topic the topic for which there is a mapping function
    * @return the Context[A] => B function for the given topic
    */
  def mappingForTopic(topic: String) = scriptForTopic(topic).flatMap(templateCache.apply)

  def makeRestRequest(record: CommittableRecord[K, V])(implicit outputAsRequest: B =:= HttpRequest): ZIO[Any, Throwable, Response[Either[String, String]]] = {
    asRestRequest(record).map(RestClient.send)
  }

  def asRestRequest(record: CommittableRecord[K, V])(implicit outputAsRequest: B =:= HttpRequest): Task[HttpRequest] = {
    for {
      asRequest <- Task.fromTry(mappingForTopic(record.record.topic))
      request   = outputAsRequest(asRequest(asContext(record)))
    } yield request
  }
}
object KafkaRecordToHttpRequest {

  import eie.io._

  def dataDir(rootConfig: Config): Path = rootConfig.getString("app.data").asPath

  def forRootConfig[K: Encoder, V: Encoder](rootConfig: Config = ConfigFactory.load(),
                                            templateCache: Cache[Expression[JsonMsg, HttpRequest]] = JsonTemplate.newCache[JsonMsg, HttpRequest]())
    : ZIO[Console, Throwable, KafkaRecordToHttpRequest[K, V, HttpRequest]] = {
    val mappingConfig: MappingConfig = MappingConfig(rootConfig)
    for {
      disk <- Disk(rootConfig)
      inst <- apply[K, V](mappingConfig, disk, templateCache) { record =>
        record.asContext(dataDir(rootConfig))
      }
    } yield inst
  }

  def apply[K: Encoder, V: Encoder](mappingConfig: MappingConfig, disk: Disk.Service, templateCache: Cache[Expression[JsonMsg, HttpRequest]])(
      asContext: JsonMsg => Context[JsonMsg]): ZIO[Console, Throwable, KafkaRecordToHttpRequest[K, V, HttpRequest]] = {
    mappingConfig.scriptForTopic(disk).map { lookup =>
      val transform: CommittableRecord[K, V] => Context[JsonMsg] = (asMessage[K, V] _).andThen(asContext)
      val foo                                                    = new KafkaRecordToHttpRequest[K, V, HttpRequest](mappingConfig, templateCache, lookup, transform)
      foo
    }
  }

  def asMessage[K: Encoder, V: Encoder](record: CommittableRecord[K, V]): JsonMsg = {
    Message(new RichDynamicJson(record.value.asJson), new RichDynamicJson(record.key.asJson), record.timestamp, KafkaRecord.headerAsStrings(record))
  }

  def writeScriptForTopic(mappingConfig: MappingConfig, disk: Disk.Service, topic: String, script: String): ZIO[Any, Serializable, Unit] = {
    for {
      pathToMapping <- ZIO.fromOption(mappingConfig.lookup(topic))
      _             <- disk.write(pathToMapping, script)
    } yield ()
  }
}