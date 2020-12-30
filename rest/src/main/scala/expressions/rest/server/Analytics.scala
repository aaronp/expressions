package expressions.rest.server

import expressions.franz.SupportedType._
import expressions.franz.{FranzConfig, SupportedType}
import io.circe.Json
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import zio.clock.Clock
import zio.{UIO, ZIO}

import java.nio.ByteBuffer
import java.time.Instant
import scala.util.Try

object Analytics {

  trait Service {

    /**
      * log that we've accepted a record
      * @param record
      * @return
      */
    def onRecord(record: ConsumerRecord[_, _]): UIO[Unit]

    def onUnmarshal(record: ConsumerRecord[_, _], key: Try[Json], value: Try[Json]): UIO[Unit]
  }

  def apply(config: FranzConfig) = {
    for {
      clock                       <- ZIO.environment[Clock]
      disk                        <- ZIO.environment[Disk]
      started                     <- clock.get.instant
//      keyType: SupportedType[_]   = config.keyType
//      valueType: SupportedType[_] = config.valueType
    } yield ??? //Inst(config, started, clock.get, disk.get, keyType, valueType)
  }

  case class Inst(config: FranzConfig, started: Instant, clock: Clock.Service, disk: Disk.Service, keyType: SupportedType[_], valueType: SupportedType[_]) extends Service {

    keyType match {
      case BYTE_ARRAY        => (record: ConsumerRecord[_, _]) => {}
      case STRING            =>
      case RECORD(namespace) =>
      case LONG              =>
    }

    def asPath(record: ConsumerRecord[_, _]) = {
      import record._
      List("data", partition(), offset()).map(_.toString)
    }

    override def onRecord(record: ConsumerRecord[_, _]): UIO[Unit] = {

//      for {
//        now <- clock.instant
//        _   <- disk.write(asPath(record) :+ "key", record, "")
//      } yield {}
      ???
    }

    override def onUnmarshal(record: ConsumerRecord[_, _], key: Try[Json], value: Try[Json]): UIO[Unit] = ???
  }
}
