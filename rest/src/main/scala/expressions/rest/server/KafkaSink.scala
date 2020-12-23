package expressions.rest.server

import com.typesafe.config.{Config, ConfigRenderOptions}
import eie.io.AlphaCounter
import expressions.Cache
import expressions.JsonTemplate.Expression
import expressions.client.{HttpRequest, HttpResponse}
import expressions.client.kafka.{ConsumerStats, RecordCoords, RecordSummary, StartedConsumer}
import expressions.franz.{ForEachStream, FranzConfig, SchemaGen}
import io.circe.syntax.EncoderOps
import io.circe.{Encoder, Json}
import org.apache.avro.generic.IndexedRecord
import zio.clock.Clock
import zio.console.Console
import zio.duration.durationInt
import zio.kafka.consumer.CommittableRecord
import zio.{Ref, _}

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

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
  type SinkIO[K, V]  = UIO[CommittableRecord[K, V] => Task[Unit]]
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
    *
    * @param config
    * @param templateCache
    * @param statsMap
    * @param clock
    * @tparam K
    * @tparam V
    * @return
    */
  def saveToDB[K: Encoder, V: Encoder](input: SinkInput,
                                       templateCache: Cache[Expression[JsonMsg, List[HttpRequest]]],
                                       statsMap: Ref[Map[String, ConsumerStats]],
                                       clock: Clock): ZIO[Console, Throwable, CommittableRecord[K, V] => Task[Unit]] = {
    val (id, config) = input
    for {
      restSink <- KafkaRecordToHttpRequest.forRootConfig[K, V](config, templateCache)
    } yield { (record: CommittableRecord[K, V]) =>
      restSink
        .makeRestRequest(record)
        .map(validate)
        .either
        .flatMap { either =>
          clock.get.instant.map(_.toEpochMilli).flatMap { nowEpoch =>
            val update = statsMap.update { byId =>
              val newStats = byId.get(id) match {
                case None         => stats.createStats(id, record, either.toTry, nowEpoch)
                case Some(before) => stats.updateStats(before, record, either.toTry, nowEpoch)
              }
              byId.updated(id, newStats)
            }
            update.as(either)
          }
        }
        .repeatUntilM(r => UIO(r.isRight).delay(1.second))
        .provide(clock)
        .unit
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
  def apply[K: Encoder, V: Encoder](templateCache: Cache[Expression[JsonMsg, List[HttpRequest]]]): ZIO[ZEnv, Nothing, Instance[K, V]] = {
    for {
      clock    <- ZIO.environment[ZEnv]
      statsMap <- Ref.make(Map[String, ConsumerStats]())
      writer = (input: SinkInput) => {
        // TODO - this is where we might infer the K/V types, rather than in this method signature
        val sink: ZIO[Console, Throwable, CommittableRecord[K, V] => Task[Unit]] = saveToDB[K, V](input, templateCache, statsMap, clock)
        // TODO - this is where we might inject some retry/recovery logic (rather than just 'orDie')

        sink.provide(clock).orDie
      }
      svc <- Service[K, V](statsMap, writer)
    } yield svc
  }

  object Service {
    def apply[K, V](statsMap: Ref[Map[String, ConsumerStats]], makeSink: SinkInput => SinkIO[K, V]): ZIO[zio.ZEnv, Nothing, Instance[K, V]] =
      for {
        env  <- ZIO.environment[ZEnv]
        byId <- Ref.make(Map[String, (StartedConsumer, Fiber[_, _])]())
      } yield
        Instance(
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
  case class Instance[K, V](tasksById: Ref[Map[RunningSinkId, (StartedConsumer, Fiber[_, _])]],
                            statsMap: Ref[Map[RunningSinkId, ConsumerStats]],
                            makeSink: SinkInput => SinkIO[K, V],
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
        kafkaFeed = ForEachStream[K, V](FranzConfig.fromRootConfig(rootConfig))(sink)
        fiber     <- kafkaFeed.runCount.fork
        _ <- tasksById.update { map =>
          val coords = StartedConsumer(id, rootConfig.root().render(ConfigRenderOptions.concise()), System.currentTimeMillis())
          val entry  = (coords, fiber)
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

  /**
    * Functions for maintaining our ConsumerStats
    */
  object stats {

    def updateStats(consumerStats: ConsumerStats, record: CommittableRecord[_, _], result: Try[List[(HttpRequest, HttpResponse)]], now: Long) = {
      val summary = RecordSummary(
        recordCoords(record),
        asMessage(result),
        asJson(result),
        now
      )
      val errors = result match {
        case Failure(_) => summary +: consumerStats.errors
        case _          => consumerStats.errors
      }
      val newRecords: List[RecordSummary] = summary :: consumerStats.recentRecords.take(10)
      consumerStats.copy(totalRecords = consumerStats.totalRecords + 1, recentRecords = newRecords, errors = errors)
    }

    def createStats(id: RunningSinkId, record: CommittableRecord[_, _], result: Try[List[(HttpRequest, HttpResponse)]], now: Long) = {
      val summary = RecordSummary(
        recordCoords(record),
        asMessage(result),
        asJson(result),
        now
      )
      val errors = result match {
        case Failure(_) => List(summary)
        case _          => Nil
      }
      ConsumerStats(id, 1, List(summary), errors)
    }

    private def recordCoords(record: CommittableRecord[_, _]): RecordCoords = RecordCoords(record.record.topic(), record.offset.offset, record.partition, asString(record.key))

    private def asMessage(request: HttpRequest, response: HttpResponse) = {
      s"${request.url} yields ${response.statusCode}"
    }

    private def asMessage(results: Try[List[(HttpRequest, HttpResponse)]]): String = {
      results match {
        case Failure(err)                        => s"Error: $err"
        case Success((request, response) :: Nil) => asMessage(request, response)
        case Success(list) =>
          list
            .map {
              case (request, response) => asMessage(request, response)
            }
            .mkString(s"${list.size} requests: [\n", "\n", "\n]")
      }
    }

    private def asJson(results: Try[List[(HttpRequest, HttpResponse)]]): Json = {
      results match {
        case Failure(err)                        => Json.obj("error" -> s"${err}".asJson)
        case Success((request, response) :: Nil) => asJson(request, response)
        case Success(list) =>
          val jsons = list.map {
            case (request, response) => asJson(request, response)
          }
          jsons.asJson
      }
    }

    private def asJson(request: HttpRequest, response: HttpResponse): Json = {
      Map(
        "request"  -> request.asJson,
        "response" -> asJson(response)
      ).asJson
    }

    private def asJson(response: HttpResponse): Json = {
      Map(
        "code" -> response.statusCode.asJson,
        "body" -> response.body.asJson
      ).asJson
    }

    @tailrec
    private def asString(value: Any): String = {
      value match {
        case null                  => "NULL"
        case jason: Json           => jason.asString.getOrElse(jason.noSpaces)
        case record: IndexedRecord => asString(SchemaGen.asJson(record))
        case other                 => other.toString
      }
    }
  }
}
