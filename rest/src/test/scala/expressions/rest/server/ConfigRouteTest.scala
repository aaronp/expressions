package expressions.rest.server

import args4c.implicits._
import com.typesafe.config.ConfigFactory
import io.circe.Json
import io.circe.syntax._

class ConfigRouteTest extends BaseRouteTest {
  "Get /config/application.conf" should {
    "always return the default config" in {
      Given("A config route under test")

      val disk = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load())
      val Some(readBackFull) = underTest(get(s"config/application.conf")).value.value()
      val readBackConfig = ConfigFactory.parseString(readBackFull.bodyAsString)

      val Some(readBackSummary) = underTest(get(s"config/application.conf?summary=true")).value.value()
      readBackSummary.bodyAs[ConfigSummary].brokers shouldBe List("localhost:9092")
      readBackSummary.bodyAs[ConfigSummary].topic should not be ("")
    }
  }
  "POST /config/save" should {
    "merge a ConfigSummary with an existing config" in {
      Given("A config route under test")
      val cfgName = newName()
      val disk = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load().withoutPath("app.mapping"))

      And("And initially saved configuration")
      val initial = ConfigSummary(
        topic = "test",
        brokers = List("12", "34"),
        mappings = Map("foo" -> List("bar")),
        keyType = "long",
        valueType = "avro:ex.ample",
        producerKeyType = "byte array",
        producerValueType = "string"
      )

      When("We save the initial configuration")
      val Some(response) = {
        val baseConfig = ConfigSummary.asJson(initial.asConfig.withFallback(ConfigFactory.load()))
        underTest(post(s"config/save/${cfgName}?fallback=true", baseConfig.noSpaces)).value.value()
      }

      Then("That should work - unless we've really fucked something up. We're not even testing yet")
      response.status.code shouldBe 200
      response.bodyAsString shouldBe "{\"success\":true}"

      When("We try and save a ConfigSummary update")
      val updated = initial.copy(topic = "changed", brokers = List("updated"))
      val Some(updatedResponse) = underTest(post(s"config/save/${cfgName}", updated.asJson.noSpaces)).value.value()

      Then("that should obs work")
      updatedResponse.status.code shouldBe 200
      updatedResponse.bodyAsString shouldBe "{\"success\":true}"

      And("when we read it back - either as a full config or a summary")
      val Some(readBackFull) = underTest(get(s"config/${cfgName}")).value.value()
      Then("It should be merged!")
      val readBackConfig = ConfigFactory.parseString(readBackFull.bodyAsString)

      readBackConfig.asList("app.franz.consumer.bootstrap.servers") should contain only ("updated")
      readBackConfig.asList("app.franz.producer.bootstrap.servers") should contain only ("updated")

      val Some(readBackSummary) = underTest(get(s"config/${cfgName}?summary=true")).value.value()
      val asSummary = readBackSummary.bodyAs[ConfigSummary]
      asSummary shouldBe updated
    }
    "be able to remove mappings" in {
      Given("A config route under test")
      val cfgName = newName()
      val disk = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load())

      And("And initially saved configuration w/ 2 mappings")
      val initial = ConfigSummary(
        topic = "test",
        brokers = List("12", "34"),
        mappings = Map("first" -> List("bar"), "second" -> List("bar")),
        keyType = "long",
        valueType = "avro:ex.ample",
        producerKeyType = "byte array",
        producerValueType = "string"
      )

      val Some(response) = underTest(post(s"config/save/${cfgName}", initial.asJson.noSpaces)).value.value()
      response.status.code shouldBe 200

      When("We save an updated config w/ fewer mappings")
      val fewerMappings = initial.copy(mappings = initial.mappings - "first")
      underTest(post(s"config/save/${cfgName}", fewerMappings.asJson.noSpaces)).value.value()

      val Some(onDisk) = disk.read(List("config", cfgName)).value()
      onDisk should not be (empty)

      Then("It have the updated, single mapping")
      val Some(readBackSummary) = underTest(get(s"config/${cfgName}?summary=true")).value.value()
      val asSummary = readBackSummary.bodyAs[ConfigSummary]
      asSummary shouldBe fewerMappings
    }
    "save a new ConfigSummary when there isn't an existing one" in {
      Given("A config route under test")
      val cfgName = newName()
      val disk = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load().withoutPath("app.mapping"))

      And("And initial summary")
      val initial = ConfigSummary(
        topic = "test",
        brokers = List("12", "34"),
        mappings = Map("foo" -> List("bar")),
        keyType = "long",
        valueType = "avro:ex.ample",
        producerKeyType = "byte array",
        producerValueType = "string"
      )

      When("We save the initial summary as a summary")
      val Some(response) = underTest(post(s"config/save/${cfgName}?fallback=true", initial.asJson.noSpaces)).value.value()

      Then("That should work")
      response.status.code shouldBe 200
      response.bodyAsString shouldBe "{\"success\":true}"

      And("when we read it back - either as a full config or a summary")
      val Some(readBackFull) = underTest(get(s"config/${cfgName}")).value.value()
      Then("It should be merged!")
      val readBackConfig = ConfigFactory.parseString(readBackFull.bodyAsString)

      readBackConfig.asList("app.franz.consumer.bootstrap.servers") should contain only("12", "34")
      readBackConfig.asList("app.franz.producer.bootstrap.servers") should contain only("12", "34")

      val Some(readBackSummary) = underTest(get(s"config/${cfgName}?summary=true")).value.value()
      val asSummary = readBackSummary.bodyAs[ConfigSummary]
      asSummary shouldBe initial
    }
    "save a full config as-is" in {
      Given("A config route under test")
      val cfgName = newName()
      val disk = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load().withoutPath("app.mapping"))

      And("And initial config")
      val initial = ConfigSummary(
        topic = "test",
        brokers = List("12", "34"),
        mappings = Map("foo" -> List("bar")),
        keyType = "long",
        valueType = "avro:ex.ample",
        producerKeyType = "byte array",
        producerValueType = "string"
      ).asConfigJson()

      When("We save the initial config")
      val Some(response) = underTest(post(s"config/save/${cfgName}?fallback=true", initial.noSpaces)).value.value()

      Then("That should work")
      response.status.code shouldBe 200
      response.bodyAsString shouldBe "{\"success\":true}"

      And("when we read it back - either as a full config or a summary")
      val Some(readBackFull) = underTest(get(s"config/${cfgName}")).value.value()
      val readBackJson = readBackFull.bodyAs[Json]
      readBackJson shouldBe initial
    }

    "delete all your data. Just Kidding. Gosh - can you imagine?!? It just saves ConfigSummaries " in {
      val cfgName = newName()
      val disk = Disk.Service().value()
      val underTest = ConfigRoute(disk, ConfigFactory.load().withoutPath("app.mapping"))

      val expected = testSummary()

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

      val cfgName = rnd(getClass.getSimpleName)
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

  def newName() = rnd(getClass.getSimpleName)

  def testSummary() = ConfigSummary(
    topic = "a",
    brokers = List("b"),
    mappings = Map("x" -> List("y")),
    keyType = "long",
    valueType = "avro:some.ns",
    producerKeyType = "byte array",
    producerValueType = "avro:acme.foo.bar"
  )

}
