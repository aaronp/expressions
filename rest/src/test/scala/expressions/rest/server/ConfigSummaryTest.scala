package expressions.rest.server

import com.typesafe.config.ConfigRenderOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConfigSummaryTest extends AnyWordSpec with Matchers {

  "ConfigSummary.asConfig" should {
    "return the config summary as a config" in {
      val summary =  ConfigSummary("some topic", List("a", "b:1234"), Map("dave*" -> List("map", "ings.sc")), "string", "avro:ignore.me")
      val jason = summary.asConfig().root.render(ConfigRenderOptions.concise())
      jason shouldBe """{"app":{"franz":{"kafka":{"brokers":"a,b:1234","key":{"deserializer":"org.apache.kafka.common.serialization.StringDeserializer","serializer":"org.apache.kafka.common.serialization.StringSerializer"},"topic":"some topic","value":{"deserializer":"io.confluent.kafka.streams.serdes.avro.GenericAvroSerializer","serializer":"io.confluent.kafka.streams.serdes.avro.GenericAvroDeserializer"}},"mapping":{"dave*":"map/ings.sc"},"namespace":"ignore.me"}}}"""
    }
  }
}
