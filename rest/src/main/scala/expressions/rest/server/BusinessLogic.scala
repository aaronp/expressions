package expressions.rest.server

import com.typesafe.config.Config
import eie.io.AlphaCounter
import expressions.franz.{ForEachStream, FranzConfig, KafkaRecord}
import zio._

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
object BusinessLogic {
  trait Service {
    def start(config: Config): ZIO[ZEnv, Nothing, String]
    def stop(key: String): Task[Boolean]
  }

  object Service {
    def apply() =
      for {
        byId <- Ref.make(Map[String, Fiber[_, _]]())
      } yield byId
  }

  case class BusinessLogicImpl(sink: KafkaRecord => Task[Unit], tasksById: Ref[Map[String, Fiber[_, _]]]) extends Service {

    private val counter = AlphaCounter.from(0)

    override def start(config: Config): ZIO[ZEnv, Nothing, String] = {
      val kafkaFeed = ForEachStream(FranzConfig(config))(sink)
      for {
        fiber <- kafkaFeed.runCount.fork
        id <- tasksById.modify { map =>
          val id = counter.next()
          id -> map.updated(id, fiber)
        }
      } yield id
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
