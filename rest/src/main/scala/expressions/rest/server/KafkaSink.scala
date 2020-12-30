package expressions.rest.server

import com.typesafe.config.{Config, ConfigRenderOptions}
import eie.io.AlphaCounter
import expressions.Cache
import expressions.JsonTemplate.Expression
import expressions.client.kafka.{ConsumerStats, StartedConsumer}
import expressions.client.{HttpRequest, HttpResponse}
import expressions.franz.{ForEachStream, FranzConfig}
import zio.kafka.consumer.CommittableRecord
import zio.{Ref, _}

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
  */
object KafkaSink {

  /**
    * A task which, when run, produces a 'sink' -- a function which persists the given record (and returns back that record)
    */
  type SinkIO        = UIO[CommittableRecord[_, _] => Task[Unit]]
  type RunningSinkId = String
  type SinkInput     = (RunningSinkId, Config)

  trait Service {
    def start(config: Config): Task[RunningSinkId]
    def stats(key: RunningSinkId): Task[Option[ConsumerStats]]
    def stop(key: RunningSinkId): Task[Boolean]
    def running(): UIO[List[StartedConsumer]]
  }

  def validate(responses: List[(HttpRequest, HttpResponse)]) = {
    responses.map {
      case (request, resp) =>
        require(resp.statusCode >= 200 && resp.statusCode < 300, s"Response code was ${resp.statusCode}")
        request -> resp
    }
  }

  /**
    * This is one way to make the sink. We could also just drop in some code we might reflectively initialized
    * from a config.
    *
    * The important thing is that we return an instance of the Sink Service
    * @param templateCache
    * @return
    */
  def apply(templateCache: Cache[Expression[JsonMsg, List[HttpRequest]]]): ZIO[ZEnv, Nothing, RunnablePipeline] = {
    for {
      clock    <- ZIO.environment[ZEnv]
      statsMap <- Ref.make(Map[String, ConsumerStats]())
      writer = (input: SinkInput) => {
        // this could be swapped out with anything that will write data down given a record input
        KafkaRecordToHttpRequest(input, templateCache, statsMap, clock).provide(clock).orDie
      }
      svc <- Service(statsMap, writer)
    } yield svc
  }

  object Service {
    def apply(statsMap: Ref[Map[String, ConsumerStats]], makeSink: SinkInput => SinkIO): ZIO[zio.ZEnv, Nothing, RunnablePipeline] =
      for {
        env  <- ZIO.environment[ZEnv]
        byId <- Ref.make(Map[String, (StartedConsumer, Fiber[_, _])]())
      } yield
        RunnablePipeline(
          byId,
          statsMap,
          makeSink,
          env
        )
  }

  /**
    * An instance which uses 'makeSink' to construct a [[CommittableRecord[K,V]]] sink which can be started/stopped
    * @param tasksById
    * @param makeSink
    * @param env
    */
  case class RunnablePipeline(tasksById: Ref[Map[RunningSinkId, (StartedConsumer, Fiber[_, _])]],
                              statsMap: Ref[Map[RunningSinkId, ConsumerStats]],
                              makeSink: SinkInput => SinkIO,
                              env: ZEnv)
      extends Service {

    private val counter = AlphaCounter.from(0)

    override def running() = tasksById.get.map { map =>
      map.values.map(_._1).toList.sortBy(_.startedAtEpoch)
    }

    override def start(rootConfig: Config): Task[RunningSinkId] = {
      val startIO = for {
        id        <- ZIO(counter.next())
        sink      <- makeSink(id, rootConfig)
        kafkaFeed = ForEachStream(FranzConfig.fromRootConfig(rootConfig))(sink)
        fiber     <- kafkaFeed.runCount.fork
        _ <- tasksById.update { map =>
          val coords = {
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
          val entry = (coords, fiber)
          map.updated(id, entry)
        }
      } yield id

      startIO.provide(env)
    }

    override def stop(key: RunningSinkId): Task[Boolean] =
      for {
        fiber <- tasksById.modify { map =>
          val fiber = map.get(key)
          fiber -> map.removed(key)
        }
        _ <- ZIO.foreach(fiber)(_._2.interrupt)
      } yield fiber.isDefined

    override def stats(taskId: RunningSinkId): Task[Option[ConsumerStats]] = statsMap.get.map(_.get(taskId))
  }

}
