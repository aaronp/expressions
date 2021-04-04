package expressions.rest.server

import com.typesafe.config.ConfigFactory
import io.circe.syntax._

class ConfigRouteTest extends BaseRouteTest {
  "POST /config/save" should {
    "delete all your data. Just Kidding. Gosh - can you imagine?!? It just saves ConfigSummaries " in {

      val cfgName   = rnd(getClass.getSimpleName)
      val disk      = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load().withoutPath("app.mapping"))
      //      val underTest = ConfigRoute(disk)

      val expected = ConfigSummary(
        topic = "a",
        brokers = List("b"),
        mappings = Map("x" -> List("y")),
        keyType = "long",
        valueType = "avro:some.ns",
        producerKeyType = "byte array",
        producerValueType = "avro:acme.foo.bar"
      )

      val Some(response) = underTest(post(s"config/save/${cfgName}?fallback=true", expected.asJson.noSpaces)).value.value()
      response.status.isSuccess shouldBe true

      val Some(readBackResponse) = underTest(get(s"config/$cfgName")).value.value()
      readBackResponse.status.isSuccess shouldBe true

      val Some(parsed) = underTest(post("config/parse", readBackResponse.bodyAsString)).value.value()
      parsed.bodyAs[ConfigSummary] shouldBe expected
    }
  }

  "GET /config" should {
    "get the default config" in {

      val cfgName   = rnd(getClass.getSimpleName)
      val underTest = ConfigRoute(Disk.Service().value())
      val Some(response) = underTest(
        post(
          s"config/save/${cfgName}",
          ConfigSummary(
            topic = "a",
            brokers = List("b"),
            mappings = Map("x" -> List("y")),
            keyType = "string",
            valueType = "long",
            producerKeyType = "bytes",
            producerValueType = "bytes"
          ).asJson.noSpaces
        )).value.value()
      response.status.isSuccess shouldBe true

      val Some(readBack) = underTest(get(s"config/$cfgName")).value.value()
      readBack.status.isSuccess shouldBe true
    }
  }

}
