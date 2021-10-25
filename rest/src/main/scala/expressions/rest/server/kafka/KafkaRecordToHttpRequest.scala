package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.CodeTemplate.Expression
import expressions.client.kafka.ConsumerStats
import expressions.client.{HttpRequest, HttpResponse, RestClient}
import expressions.franz.{FranzConfig, KafkaRecord, SupportedType}
import expressions.rest.server.{Disk, JsonMsg, MappingConfig, Stats, Topic}
import expressions.template.{Context, Message}
import expressions.{Cache, DynamicJson}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import zio.clock.Clock
import zio.console.Console
import zio.kafka.consumer.CommittableRecord
import zio.{Ref, Task, ZIO}

import java.nio.file.Path
import scala.util.Try

/**
  * @param transformForTopic a function which can lookup a context transformation for a given topic
  * @param asContext a function which can produce a 'Context' for a Kafka record
  * @tparam B
  */
final case class KafkaRecordToHttpRequest[B](transformForTopic: Topic => Try[Expression[JsonMsg, B]], asContext: CommittableRecord[_, _] => Context[JsonMsg])

object KafkaRecordToHttpRequest extends StrictLogging {

  import eie.io._

  /**
    * Given a SinkInput (an identifier and a configuration),
    * @param input an application ID and configuration pair
    */
  def apply(input: SinkInput,
            templateCache: Cache[Expression[JsonMsg, Seq[HttpRequest]]],
            statsMap: Ref[Map[RunningSinkId, ConsumerStats]],
            clock: Clock): ZIO[Console, Throwable, CommittableRecord[_, _] => Task[Unit]] = {
    val (jobId, config: Config) = input

    /**
      * TODO - set some recovery here - e.g. retry forever w/ exponential backoff
      * @param requests the requests to make
      * @return a task which returns the requests paired with the results
      */
    def makeRequests(requests: Seq[HttpRequest]): ZIO[Any, Throwable, Seq[(HttpRequest, HttpResponse)]] = {
      ZIO.foreach(requests) { r =>
        logger.info(s"⚡ SENDING ⚡ ${r}")
        Task.fromFuture(_ => RestClient.send(r)).map(r -> _)
      }
    }

    asRequests(config, templateCache).map { requestsForRecord => (record: CommittableRecord[_, _]) =>
      val writeToRestEndpoint = for {
        requests: Seq[HttpRequest]                <- requestsForRecord(record)
        results: Seq[(HttpRequest, HttpResponse)] <- makeRequests(requests)
        _                                         <- KafkaErr.validate(record, results)
      } yield results

      // update stats
      writeToRestEndpoint.either.flatMap { either =>
        clock.get.instant.map(_.toEpochMilli).flatMap { nowEpoch =>
          val update = statsMap.update { byId =>
            val newStats = byId.get(jobId) match {
              case None         => Stats.createStats(jobId, record, either.toTry, nowEpoch)
              case Some(before) => Stats.updateStats(before, record, either.toTry, nowEpoch)
            }
            byId.updated(jobId, newStats)
          }
          update.unit
        }
      }
    }
  }

  /**
    * @param config the root configuration from which we can take the Kafka serde functions
    * @param templateCache the expression compiler
    * @return  a function that either converts records into [[HttpRequest]]s or fails with a [[KafkaErr]]
    */
  def asRequests(config: Config,
                 templateCache: Cache[Expression[JsonMsg, Seq[HttpRequest]]]): ZIO[Console, Throwable, CommittableRecord[_, _] => ZIO[Any, KafkaErr, Seq[HttpRequest]]] = {
    for {
      _        <- ZIO(logger.info(s"☕ Compiling ☕"))
      restSink <- forRootConfig(config, templateCache)
    } yield { (record: CommittableRecord[_, _]) =>
      for {
        context: Context[JsonMsg] <- Task(restSink.asContext(record)).refineOrDie {
          case transformError => KafkaErr.conversionError(record, transformError)
        }
        contextAsRequests: Expression[JsonMsg, Seq[HttpRequest]] <- Task.fromTry(restSink.transformForTopic(record.record.topic)).refineOrDie {
          case compileError => KafkaErr.compileExpressionError(record, compileError)
        }
        requests <- Task(contextAsRequests(context)).refineOrDie {
          case expressionError => KafkaErr.expressionError(record, contextAsRequests, expressionError)
        }
      } yield requests
    }
  }

  /**
    *
    * @param rootConfig
    * @param templateCache
    * @return a function which can transform ConsumerRecords into a database write
    */
  def forRootConfig(rootConfig: Config = ConfigFactory.load(),
                    templateCache: Cache[Expression[JsonMsg, Seq[HttpRequest]]]): ZIO[Console, Throwable, KafkaRecordToHttpRequest[Seq[HttpRequest]]] = {
    val mappingConfig: MappingConfig = MappingConfig(rootConfig)
    val fsDir                        = dataDir(rootConfig)
    val recordAsContext: CommittableRecord[_, _] => Context[Message[DynamicJson, DynamicJson]] = SupportedType
      .AsJson(FranzConfig.fromRootConfig(rootConfig))
      .andThen(asMessage[Json, Json])
      .andThen(_.asContext(fsDir))

    for {
      disk           <- Disk(rootConfig)
      scriptLookup   <- mappingConfig.scriptForTopic(disk)
      scriptForTopic = (topic: Topic) => scriptLookup(topic).flatMap(templateCache.apply)
      transform      = new KafkaRecordToHttpRequest[Seq[HttpRequest]](scriptForTopic, recordAsContext)
    } yield transform
  }

  def asMessage[K: Encoder, V: Encoder](record: CommittableRecord[K, V]): JsonMsg = Batch.asMessage[K, V](record)

  def writeScriptForTopic(mappingConfig: MappingConfig, disk: Disk.Service, topic: String, script: String): ZIO[Any, Any, Unit] = {
    for {
      pathToMapping <- ZIO.fromOption(mappingConfig.lookup(topic)).catchSome {
        case None => ZIO.fail(new Exception(s"No mapping found for topic '$topic' in $mappingConfig"))
      }
      _ <- disk.write(pathToMapping, script)
    } yield ()
  }

  def dataDir(rootConfig: Config): Path = rootConfig.getString("app.data").asPath
}
