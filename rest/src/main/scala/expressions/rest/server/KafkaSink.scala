package expressions.rest.server

import com.typesafe.config.Config
import eie.io.AlphaCounter
import expressions.JsonTemplate.Expression
import expressions.client.HttpRequest
import expressions.franz.{ForEachStream, FranzConfig}
import expressions.{Cache, RichDynamicJson}
import io.circe.Encoder
import sttp.client.Response
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.kafka.consumer.CommittableRecord

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
  type SinkIO[K, V] = UIO[CommittableRecord[K, V] => Task[Unit]]

  trait Service {
    def start(config: Config): Task[String]
    def stop(key: String): Task[Boolean]
    def running(): UIO[List[String]]
  }

  def validate(response: Response[Either[String, String]]) = {
    response.body match {
      case Left(error) =>
        sys.error(s"Response threw $error")
      case Right(_) =>
        require(response.code.isSuccess, s"Response code was ${response.statusText}")
    }
  }

  def saveToDB[K: Encoder, V: Encoder](config: Config,
                                       templateCache: Cache[Expression[JsonMsg, HttpRequest]],
                                       clock: Clock): ZIO[Console, Throwable, CommittableRecord[K, V] => Task[Unit]] = {
    for {
      restSink <- KafkaRecordToHttpRequest.forRootConfig[K, V](config, templateCache)
    } yield { (record: CommittableRecord[K, V]) =>
      val sink: ZIO[Any, Throwable, Response[Either[String, String]]] = restSink.makeRestRequest(record)
      val sunk: Task[Unit] = sink
        .map(validate)
        .either
        .repeatUntilM(r => UIO(r.isRight).delay(1.second))
        .provide(clock)
        .unit

      sunk
    }
  }

  /**
    * This is one way to make the sink. We could also just drop in some code we might reflectively init
    * from a config
    * @param templateCache
    * @return
    */
  def apply[K: Encoder, V: Encoder](templateCache: Cache[Expression[JsonMsg, HttpRequest]]): ZIO[ZEnv, Nothing, Instance[K, V]] = {
    for {
      clock <- ZIO.environment[ZEnv]
      writer = (config: Config) => {
        // TODO - this is where we might infer the K/V types
        val sink = saveToDB[K, V](config, templateCache, clock)
        // TODO - this is where we might inject some retry/recovery logic (rather than just 'orDie')
        sink.provide(clock).orDie
      }
      svc <- Service[K, V](writer)
    } yield svc
  }

  object Service {
    def apply[K, V](makeSink: Config => SinkIO[K, V]): ZIO[zio.ZEnv, Nothing, Instance[K, V]] =
      for {
        env  <- ZIO.environment[ZEnv]
        byId <- Ref.make(Map[String, Fiber[_, _]]())
      } yield
        Instance(
          byId,
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
  case class Instance[K, V](tasksById: Ref[Map[String, Fiber[_, _]]], makeSink: Config => SinkIO[K, V], env: ZEnv) extends Service {

    private val counter = AlphaCounter.from(0)

    override def running(): UIO[List[String]] = tasksById.get.map(_.keys.toList.sorted)

    override def start(rootConfig: Config): Task[String] = {
      val startIO = for {
        sink      <- makeSink(rootConfig)
        kafkaFeed = ForEachStream[K, V](FranzConfig.fromRootConfig(rootConfig))(sink)
        fiber     <- kafkaFeed.runCount.fork
        id <- tasksById.modify { map =>
          val id = counter.next()
          id -> map.updated(id, fiber)
        }
      } yield id
      startIO.provide(env)
    }

    override def stop(key: String): Task[Boolean] =
      for {
        fiber <- tasksById.modify { map =>
          val fiber = map.get(key)
          fiber -> map.removed(key)
        }
        _ <- ZIO.foreach(fiber)(_.interrupt)
      } yield fiber.isDefined
  }
}
