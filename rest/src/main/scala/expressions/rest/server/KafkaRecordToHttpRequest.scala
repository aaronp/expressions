package expressions.rest.server

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.StrictLogging
import expressions.JsonTemplate.Expression
import expressions.client.kafka.ConsumerStats
import expressions.client.{HttpRequest, HttpResponse, RestClient}
import expressions.franz.{FranzConfig, KafkaRecord, SupportedType}
import expressions.rest.server.KafkaSink.{RunningSinkId, SinkInput}
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

  sealed trait KafkaErr extends Exception {
    def fail: Task[Nothing] = {
      Task.fail(this)
    }
    def widen: KafkaErr = {
      logger.error(s"SINK ERROR: ${this}")
      this
    }
  }
  object KafkaErr {

    def conversionError(record: CommittableRecord[_, _], exp: Throwable)                                   = ConvertToJsonError(record, exp).widen
    def compileExpressionError(record: CommittableRecord[_, _], context: Context[JsonMsg], exp: Throwable) = CompileExpressionError(record, context, exp).widen
    def expressionError(record: CommittableRecord[_, _], context: Context[JsonMsg], expr: Expression[JsonMsg, Seq[HttpRequest]], exp: Throwable) =
      ExpressionError(record, context, expr, exp).widen
    def restError(record: CommittableRecord[_, _], failed: Seq[(HttpRequest, HttpResponse)]) =
      RestError(record, failed).widen

    def coords(r: CommittableRecord[_, _]) = s"{${r.record.topic()}:${r.partition}/${r.offset.offset}@${r.key}}"

    case class ConvertToJsonError(record: CommittableRecord[_, _], exception: Throwable)
        extends Exception(s"Couldn't do json conversion for ${coords(record)}: ${exception.getMessage}", exception)
        with KafkaErr
    case class CompileExpressionError(record: CommittableRecord[_, _], context: Context[JsonMsg], exception: Throwable)
        extends Exception(s"Couldn't create an expression for ${coords(record)}: ${exception.getMessage}\nWithContext:\n$context", exception)
        with KafkaErr
    case class ExpressionError(record: CommittableRecord[_, _], context: Context[JsonMsg], expr: Expression[JsonMsg, Seq[HttpRequest]], exception: Throwable)
        extends Exception(s"Couldn't execute expression $expr for ${coords(record)}: ${exception.getMessage}\nWithContext:\n$context", exception)
        with KafkaErr
    case class RestError(record: CommittableRecord[_, _], failed: Seq[(HttpRequest, HttpResponse)])
        extends Exception(s"Not all rest results returned successfully: ${failed}")
        with KafkaErr

    def validate(record: CommittableRecord[_, _], results: Seq[(HttpRequest, HttpResponse)]): ZIO[Any, Throwable, Unit] = {
      if (results.map(_._2.statusCode).forall(_ == 200)) {
        ZIO.unit
      } else {
        restError(record, results).fail
      }
    }
  }

  /**
    *
    * @param input an application ID and configuration pair
    * @param templateCache
    * @param statsMap
    * @param clock
    * @tparam K
    * @tparam V
    * @return
    */
  def apply(input: SinkInput,
            templateCache: Cache[Expression[JsonMsg, Seq[HttpRequest]]],
            statsMap: Ref[Map[RunningSinkId, ConsumerStats]],
            clock: Clock): ZIO[Console, Throwable, CommittableRecord[_, _] => Task[Unit]] = {
    val (id, config: Config) = input

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
      //        // TODO - this is where we might inject some retry/recovery logic (rather than just 'orDie')
      //        .repeatUntilM(r => UIO(r.isRight).delay(1.second))
      //        .provide(clock)
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
            val newStats = byId.get(id) match {
              case None         => Stats.createStats(id, record, either.toTry, nowEpoch)
              case Some(before) => Stats.updateStats(before, record, either.toTry, nowEpoch)
            }
            byId.updated(id, newStats)
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
          case compileError => KafkaErr.compileExpressionError(record, context, compileError)
        }
        requests <- Task(contextAsRequests(context)).refineOrDie {
          case expressionError => KafkaErr.expressionError(record, context, contextAsRequests, expressionError)
        }
      } yield requests
    }
  }

  def dataDir(rootConfig: Config): Path = rootConfig.getString("app.data").asPath

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

  def writeScriptForTopic(mappingConfig: MappingConfig, disk: Disk.Service, topic: String, script: String): ZIO[Any, Serializable, Unit] = {
    for {
      pathToMapping <- ZIO.fromOption(mappingConfig.lookup(topic))
      _             <- disk.write(pathToMapping, script)
    } yield ()
  }
}
