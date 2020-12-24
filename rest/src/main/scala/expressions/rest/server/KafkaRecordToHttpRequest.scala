package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import expressions.JsonTemplate.Expression
import expressions.client.{HttpRequest, HttpResponse, RestClient}
import expressions.franz.KafkaRecord
import expressions.template.{Context, Message}
import expressions.{Cache, RichDynamicJson}
import io.circe.Encoder
import io.circe.syntax.EncoderOps
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

  def makeRestRequest(record: CommittableRecord[K, V])(implicit outputAsRequest: B =:= List[HttpRequest]): ZIO[Any, Throwable, List[(HttpRequest, HttpResponse)]] = {
    for {
      requests <- asRestRequests(record)
      results <- ZIO.foreach(requests) { r =>
        Task.fromFuture(_ => RestClient.send(r)).map(r -> _)
      }
    } yield results
  }

  def asRestRequests(record: CommittableRecord[K, V])(implicit outputAsRequest: B =:= List[HttpRequest]): Task[List[HttpRequest]] = {
    for {
      asRequest <- Task.fromTry(mappingForTopic(record.record.topic))
      request   = outputAsRequest(asRequest(asContext(record)))
    } yield request
  }
}
object KafkaRecordToHttpRequest {

  import eie.io._

  def dataDir(rootConfig: Config): Path = rootConfig.getString("app.data").asPath

  def forRootConfig[K: Encoder, V: Encoder](
      rootConfig: Config = ConfigFactory.load(),
      templateCache: Cache[Expression[JsonMsg, List[HttpRequest]]]): ZIO[Console, Throwable, KafkaRecordToHttpRequest[K, V, List[HttpRequest]]] = {
    val mappingConfig: MappingConfig = MappingConfig(rootConfig)
    for {
      disk <- Disk(rootConfig)
      inst <- apply[K, V](mappingConfig, disk, templateCache) { record =>
        record.asContext(dataDir(rootConfig))
      }
    } yield inst
  }

  def apply[K: Encoder, V: Encoder](mappingConfig: MappingConfig, disk: Disk.Service, templateCache: Cache[Expression[JsonMsg, List[HttpRequest]]])(
      asContext: JsonMsg => Context[JsonMsg]): ZIO[Console, Throwable, KafkaRecordToHttpRequest[K, V, List[HttpRequest]]] = {
    mappingConfig.scriptForTopic(disk).map { lookup =>
      val transform: CommittableRecord[K, V] => Context[JsonMsg] = (asMessage[K, V] _).andThen(asContext)
      new KafkaRecordToHttpRequest[K, V, List[HttpRequest]](mappingConfig, templateCache, lookup, transform)
    }
  }

  def asMessage[K: Encoder, V: Encoder](record: CommittableRecord[K, V]): JsonMsg = {
    Message(
      new RichDynamicJson(record.value.asJson),
      new RichDynamicJson(record.key.asJson),
      record.timestamp,
      KafkaRecord.headerAsStrings(record),
      record.record.topic(),
      record.offset.offset,
      record.partition
    )
  }

  def writeScriptForTopic(mappingConfig: MappingConfig, disk: Disk.Service, topic: String, script: String): ZIO[Any, Serializable, Unit] = {
    for {
      pathToMapping <- ZIO.fromOption(mappingConfig.lookup(topic))
      _             <- disk.write(pathToMapping, script)
    } yield ()
  }
}
