package expressions.rest.server.kafka

import com.typesafe.config.{Config, ConfigRenderOptions}
import com.typesafe.scalalogging.StrictLogging
import eie.io.AlphaCounter
import expressions.client.kafka.{ConsumerStats, StartedConsumer}
import expressions.franz.{BatchedStream, FranzConfig}
import expressions.rest.Main
import zio.kafka.consumer.CommittableRecord
import zio.{Fiber, Ref, Task, UIO, ZEnv, ZIO}

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
      byId     <- Ref.make(Map[String, (StartedConsumer, Fiber[_, _])]())
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

  /**
    * An instance which uses 'makeSink' to construct a [[CommittableRecord[K,V]]] sink which can be started/stopped
    */
  case class RunnablePipeline(tasksById: Ref[Map[RunningSinkId, (StartedConsumer, Fiber[_, _])]],
                              statsMap: Ref[Map[RunningSinkId, ConsumerStats]],
                              makeSink: SinkInput => SinkIO,
                              env: ZEnv)
      extends KafkaSink.Service
      with StrictLogging {

    private val counter = AlphaCounter.from(0)

    override def running(): UIO[List[StartedConsumer]] = tasksById.get.map { map =>
      map.values.map(_._1).toList.sortBy(_.startedAtEpoch)
    }

    override def start(rootConfig: Config): Task[RunningSinkId] = {
      val startIO = for {
        id                     <- ZIO(counter.next())
        _                      = logger.info(s"\nStarting $id using:\n${Main.configSummary(rootConfig)}\n")
        _                      <- statsMap.update(_.updated(id, ConsumerStats(id)))
        sink                   <- makeSink(id, rootConfig)
        franzConfig            = FranzConfig.fromRootConfig(rootConfig)
        batcher: Batch.ByTopic = Batch.ByTopic(franzConfig)
        kafkaFeed              = BatchedStream(franzConfig).run(records => persist(records, batcher, sink))
        fiber                  <- kafkaFeed.runCount.fork
        _ <- tasksById.update { map =>
          val consumer = startedConsumerFor(rootConfig, id)
          map.updated(id, (consumer, fiber))
        }
      } yield id

      startIO.provide(env)
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
        _ <- ZIO.foreach_(fiber)(_._2.interrupt)
      } yield fiber.isDefined

    override def stats(taskId: RunningSinkId): Task[Option[ConsumerStats]] = {
      statsMap.get.map { byId =>
        byId.get(taskId)
      }
    }
  }
}
