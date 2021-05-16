package expressions.franz

import com.typesafe.config.Config
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.serialization.{Deserializer, Serializer}
import org.slf4j.LoggerFactory
import zio.{RIO, Task, ZIO}
import zio.kafka.serde
import zio.kafka.serde.Serde

import scala.util.{Failure, Success, Try}

case class SerdeParser(config: FranzConfig) {
  import config.*

  def serdeFor[A](serdeConfig: Config, isKey: Boolean): Task[Serde[Any, A]] = {
    val deserializerName = serdeConfig.getString("deserializer")

    deserializerName.toLowerCase match {
      case "string" | "strings" => Task(Serde.string.asInstanceOf[Serde[Any, A]])
      case "long" | "longs"     => Task(Serde.long.asInstanceOf[Serde[Any, A]])
      case _ =>
        val kafkaDeserializer: Deserializer[A] = instantiate[Deserializer[A]](deserializerName)
        val kafkaSerializer: Serializer[A]     = instantiate[Serializer[A]](serdeConfig.getString("serializer"))

        for {
          deserializer                         <- zio.kafka.serde.Deserializer.fromKafkaDeserializer[A](kafkaDeserializer, consumerSettings.properties, isKey)
          serializer: serde.Serializer[Any, A] <- zio.kafka.serde.Serializer.fromKafkaSerializer[A](kafkaSerializer, consumerSettings.properties, isKey)
        } yield {
          val d = deserializer.asTry.map {
            case Success(ok) => ok
            case Failure(err) =>
              LoggerFactory.getLogger(getClass).error(s"BANG: Deserialise failed with ${err}", err)
              throw err
          }
          val s = new serde.Serializer[Any, A] {
            override def serialize(topic: String, headers: Headers, value: A): RIO[Any, Array[Byte]] = {
              try {
                serializer.serialize(topic, headers, value).catchAll { err =>
                  LoggerFactory.getLogger(classOf[FranzConfig]).error(s"BANG2: serializer.serialize threw ${err}", err)
                  ZIO(
                    LoggerFactory.getLogger(classOf[FranzConfig]).error(s"BANG2: serializer.serialize threw ${err}", err)
                  ) *> ZIO.fail(err)
                }
              } catch {
                case err =>
                  LoggerFactory.getLogger(classOf[FranzConfig]).error(s"BANG: serializer.serialize failed with ${err}", err)
                  throw err
              }
            }
          }
          Serde[Any, A](d)(s)
        }
    }
  }

}
