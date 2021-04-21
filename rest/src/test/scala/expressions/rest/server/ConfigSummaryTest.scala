package expressions.rest.server

import com.typesafe.config.ConfigRenderOptions
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ConfigSummaryTest extends AnyWordSpec with Matchers {

  "ConfigSummary.asConfig" should {
    "return the config summary as a config" in {
      val summary = ConfigSummary("some topic", List("a", "b:1234"), Map("dave*" -> List("map", "ings.sc")), "string", "avro:ignore.me", "long", "bytes")
      val jason   = summary.asConfig().root.render(ConfigRenderOptions.concise())
      val expected =
        """{"app":{"franz":{"consumer":{"bootstrap":{"servers":"a,b:1234"},"key":{"deserializer":"org.apache.kafka.common.serialization.StringDeserializer","serializer":"org.apache.kafka.common.serialization.StringSerializer"},"namespace":"ignore.me","topic":"some topic","value":{"deserializer":"io.confluent.kafka.streams.serdes.avro.GenericAvroDeserializer","serializer":"io.confluent.kafka.streams.serdes.avro.GenericAvroSerializer"}},"producer":{"bootstrap":{"servers":"a,b:1234"},"key":{"deserializer":"org.apache.kafka.common.serialization.LongDeserializer","serializer":"org.apache.kafka.common.serialization.LongSerializer"},"topic":"some topic","value":{"deserializer":"bytes","serializer":"bytes"}}},"mapping":{"dave*":"map/ings.sc"}}}"""
      withClue(io.circe.parser.parse(jason).toTry.get.spaces2) {
        jason shouldBe expected
      }
    }
  }
}
