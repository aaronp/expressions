package expressions.rest.server

import com.typesafe.config.Config
import expressions.client.HttpRequest
import expressions.franz.{DataGen, FranzConfig, SchemaGen}
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient

import scala.util.Success

class SchemaRegistryIntegrationTest extends BaseRouteTest {

  "SchemaRegistry" should {
    "be able to list all topics" in {
      val cfg    = FranzConfig.fromRootConfig(testConfig())
      val client = cfg.schemaRegistryClient
      val all    = client.metadataById
      all should not be (empty)
      val text = all.map {
        case (subject, md) =>
          val Success(schema) = SchemaGen.parseSchema(md.getSchema)
          val testData        = DataGen.forSchema(schema)

          s"""= = = = = = = = = = = = = = = = = = = = = =
             |$subject (id : ${md.getId} version ${md.getVersion})
             |---------------------
             |${testData.spaces2}
             |
             |""".stripMargin

      }

      println(text.mkString("\n"))
    }
  }
}
