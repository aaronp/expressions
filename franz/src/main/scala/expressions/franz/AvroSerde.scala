package expressions.franz

import com.typesafe.config.{Config, ConfigFactory}
import io.confluent.kafka.streams.serdes.avro.{GenericAvroDeserializer, GenericAvroSerializer}
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.{Serde, Serdes}

object AvroSerde {
  def generic(schemasConfig: Config = ConfigFactory.load().getConfig("app.franz.schemas")): Serde[GenericRecord] = {
    val isForRecordKeys = schemasConfig.getBoolean("isForRecordKeys")
    val props           = ConfigAsJavaMap(schemasConfig)

    val ser = new GenericAvroSerializer
    ser.configure(props, isForRecordKeys)

    val de = new GenericAvroDeserializer
    de.configure(props, isForRecordKeys)
    Serdes.serdeFrom(ser, de)
  }
}
