package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigFactory}
import eie.io._
import expressions.client.HttpRequest
import expressions.client.kafka.ConsumerStats
import expressions.franz.{FranzConfig, KafkaRecord}
import expressions.rest.server.kafka.BatchProcessor.dataDir
import expressions.rest.server.{Disk, JsonMsg, MappingConfig, Stats, Topic}
import expressions.template.Message
import expressions.{Cache, DynamicJson}
import io.circe.Encoder
import io.circe.syntax._
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.kafka.consumer.CommittableRecord
import zio.{Ref, Task, URIO, ZEnv, ZIO}

import java.nio.file.Path
import scala.util.Try

final case class BatchProcessor(transformForTopic: Topic => Try[OnBatch], mappingConfig: MappingConfig, config: FranzConfig) {
  lazy val fsDir: Path = dataDir(mappingConfig.rootConfig)
}

/**
  * This is the bit which puts together the script lookup by topic.
  *
  * The whole point is just to be able to process batches based on a topic
  */
object BatchProcessor {

  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
  type Handler = Batch => ZIO[ZEnv, Throwable, Unit]

  val make: URIO[ZEnv, SinkInput => SinkIO] = {
    for {
      stats <- Ref.make(Map[RunningSinkId, ConsumerStats]())
      cache = BatchTemplate.cached
    } yield BatchProcessor(_, cache, stats)
  }

  /**
    * Given a SinkInput (an identifier and a configuration),
    * @param input an application ID and configuration pair
    */
  def apply(input: SinkInput, templateCache: Cache[OnBatch], statsMap: Ref[Map[RunningSinkId, ConsumerStats]]): SinkIO = {
    val (jobId, config) = input
    for {
      handler <- createOnBatchHandler(config, templateCache)
      env     <- ZIO.environment[ZEnv]
    } yield { batch => //: Batch =>
      handler(batch).either.flatMap { either =>
        env.get[Clock.Service].instant.map(_.toEpochMilli).flatMap { nowEpoch =>
          val update = statsMap.update { byId =>
            val newStats = byId.get(jobId) match {
              case None         => Stats.createStats(jobId, batch, either.toTry, nowEpoch)
              case Some(before) => Stats.updateStats(before, batch, either.toTry, nowEpoch)
            }
            byId.updated(jobId, newStats)
          }
          update.unit
        }
      }
    }
  }

  /**
    * @param rootConfig the root configuration from which we can take the Kafka serde functions
    * @param templateCache the expression compiler
    * @return  a function that either converts records into [[HttpRequest]]s or fails with a [[KafkaErr]]
    */
  def createOnBatchHandler(rootConfig: Config, templateCache: Cache[OnBatch] = BatchTemplate.cached): ZIO[Console with Blocking, Throwable, Handler] = {
    BatchContext(rootConfig).use { context =>
      for {
        _         <- ZIO(logger.info("☕ Compiling ☕"))
        processor <- forRootConfig(rootConfig, templateCache)
      } yield { (batch: Batch) =>
        for {
          handler <- Task.fromTry(processor.transformForTopic(batch.topic)).refineOrDie {
            case compileError => BatchErr.CompileExpressionError(batch, compileError)
          }
          result <- handler(BatchInput(batch, context))
        } yield result
      }
    }
  }

  /**
    *
    * @param rootConfig
    * @param templateCache
    * @return a function which can transform ConsumerRecords into a database write
    */
  def forRootConfig(rootConfig: Config = ConfigFactory.load(), templateCache: Cache[OnBatch]): ZIO[Console, Throwable, BatchProcessor] = {
    val mappingConfig: MappingConfig = MappingConfig(rootConfig)

    val config: FranzConfig = FranzConfig.fromRootConfig(rootConfig)

    for {
      disk           <- Disk(rootConfig)
      scriptLookup   <- mappingConfig.scriptForTopic(disk)
      scriptForTopic = (topic: Topic) => scriptLookup(topic).flatMap(templateCache.apply)
      transform      = new BatchProcessor(scriptForTopic, mappingConfig, config)
    } yield transform
  }

  def asMessage[K: Encoder, V: Encoder](record: CommittableRecord[K, V]): JsonMsg = {
    Message(
      DynamicJson(record.value.asJson),
      DynamicJson(record.key.asJson),
      record.timestamp,
      KafkaRecord.headerAsStrings(record),
      record.record.topic(),
      record.offset.offset,
      record.partition
    )
  }
  def dataDir(rootConfig: Config): Path = rootConfig.getString("app.data").asPath
}
