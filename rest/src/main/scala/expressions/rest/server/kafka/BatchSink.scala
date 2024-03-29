package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigRenderOptions}
import eie.io.AlphaCounter
import expressions.client.kafka.{ConsumerStats, RecordCoords, RecordSummary, StartedConsumer}
import expressions.franz.{BatchedStream, FranzConfig}
import expressions.rest.Main
import io.circe.Json
import zio.kafka.consumer.CommittableRecord
import zio.{Fiber, Ref, Task, UIO, ZEnv, ZIO, Has}
import zio.console.Console

/**
  * We have (separate to the kafka config) a list of:
  *
  * {{{
  *   app.mapping {
  *      <some topic name/regex> : "path/to/a/file.sc"
  *   }
  * }}}
  *
  */
object BatchSink {

  val make: ZIO[ZEnv, Nothing, KafkaSink.Service] = {
    for {
      env      <- ZIO.environment[ZEnv]
      statsMap <- Ref.make(Map[String, ConsumerStats]())
      makeSink <- BatchProcessor.make
      byId     <- Ref.make(Map[String, RunningTask]())
    } yield
      RunnablePipeline(
        byId,
        statsMap,
        makeSink,
        env
      )
  }

  def persist(records: IterableOnce[CommittableRecord[_, _]], batcher: Batch.ByTopic, sink: Batch => BatchResult): zio.RIO[ZEnv, Unit] = {
    val batches = batcher.forRecords(records)
    ZIO
      .foreach(batches) { batch =>
        sink(batch)
      }
      .unit
  }

  case class RunningTask(consumer: StartedConsumer, output: BufferConsole, runningSink: Fiber[_, _])

  /**
    * An instance which uses 'makeSink' to construct a [[CommittableRecord[K,V]]] sink which can be started/stopped
    */
  case class RunnablePipeline(tasksById: Ref[Map[RunningSinkId, RunningTask]], statsMap: Ref[Map[RunningSinkId, ConsumerStats]], makeSink: SinkInput => SinkIO, env: ZEnv)
      extends KafkaSink.Service {
    private val logger  = org.slf4j.LoggerFactory.getLogger(getClass)
    private val counter = AlphaCounter.from(System.currentTimeMillis())

    override def running(): UIO[List[StartedConsumer]] = tasksById.get.map { map =>
      map.values.map(_.consumer).toList.sortBy(_.startedAtEpoch)
    }

    def asCoords(r: CommittableRecord[_, _]): RecordCoords = RecordCoords(r.record.topic(), r.offset.offset, r.partition, r.key.toString)

    def asSummary(records: Array[CommittableRecord[_, _]]): Seq[RecordSummary] = {
      records.toIndexedSeq.map { r =>
        RecordSummary(asCoords(r), r.value.toString, Json.fromString(r.value.toString), r.timestamp)
      }
    }

    override def start(rootConfig: Config): Task[RunningSinkId] = {
      BufferConsole.make.flatMap { buffer =>
        val startIO = for {
          id                     <- ZIO(counter.next())
          _                      = logger.info(s"\nStarting $id using:\n${Main.configSummary(rootConfig)}\n")
          _                      <- statsMap.update(_.updated(id, ConsumerStats(id)))
          sink                   <- makeSink(id, rootConfig)
          franzConfig            = FranzConfig.fromRootConfig(rootConfig)
          batcher: Batch.ByTopic = Batch.ByTopic(franzConfig)
          batchedStream          <- BatchedStream(franzConfig)
          kafkaFeed = batchedStream.run { records =>
            for {
              _ <- statsMap.update { map =>
                val consumerStats: ConsumerStats = map.getOrElse(id, ConsumerStats(id))
                map.updated(id, consumerStats ++ asSummary(records))
              }
              _ <- persist(records, batcher, sink)
            } yield ()
          }
          fiber <- kafkaFeed.runCount.fork
          _ <- tasksById.update { map =>
            val consumer = startedConsumerFor(rootConfig, id)
            map.updated(id, RunningTask(consumer, buffer, fiber))
          }
        } yield id

        startIO.provide(env ++ Has[Console.Service](buffer))
      }

    }

    def startedConsumerFor(rootConfig: Config, id: RunningSinkId): StartedConsumer = {
      val configStr = {
        val franz    = rootConfig.withOnlyPath("app.franz")
        val mappings = rootConfig.withOnlyPath("app.mappings")
        franz
          .withFallback(mappings)
          .root()
          .render(ConfigRenderOptions.concise())
      }
      StartedConsumer(id, configStr, System.currentTimeMillis())
    }

    override def stop(key: RunningSinkId): Task[Boolean] =
      for {
        fiber <- tasksById.modify { map =>
          val fiber = map.get(key)
          fiber -> map.removed(key)
        }
        _ <- ZIO.foreach_(fiber)(_.runningSink.interrupt)
      } yield fiber.isDefined

    override def stats(taskId: RunningSinkId): Task[Option[ConsumerStats]] = {
      statsMap.get.map { byId =>
        byId.get(taskId)
      }
    }
  }
}
