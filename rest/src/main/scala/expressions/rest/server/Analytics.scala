package expressions.rest.server

import com.typesafe.config.Config
import expressions.franz.{FranzConfig, SupportedType}
import io.circe.Json
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.{UIO, ZIO}
import zio.clock.Clock

import java.time.Instant
import scala.util.Try

object Analytics {

  trait Service {

    /**
      * log that we've accepted a record
      * @param record
      * @return
      */
    def onRecord(record : ConsumerRecord[_,_]) : UIO[Unit]

    def onUnmarshal(record : ConsumerRecord[_,_], key : Try[Json], value : Try[Json]) : UIO[Unit]
  }

  def apply(config: FranzConfig)  = {
    for {
    clock <- ZIO.environment[Clock]
    disk <- ZIO.access[Disk](_.get)
    started <- clock.get.instant
      keyType: SupportedType[_] = config.keyType
      valueType: SupportedType[_] = config.valueType
    } yield Inst(config, started, clock.get, keyType, valueType)
  }

  case class Inst(config: FranzConfig,
                  started : Instant,
                  clock : Clock.Service,
                  disk : Disk.Service,
                  keyType: SupportedType[_],
                  valueType: SupportedType[_]) extends Service {
    override def onRecord(record: ConsumerRecord[_, _]): UIO[Unit] =
      for {
      now <- clock.instant
      _ <- disk.write(List(""), "")
      } yield {

      }

    override def onUnmarshal(record: ConsumerRecord[_, _], key: Try[Json], value: Try[Json]): UIO[Unit] = ???
  }
}
