package expressions.franz

import io.circe.Json
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SchemaGenTest extends BaseFranzTest {

  "SchemaGen" should {
    "generate an avro schema from simple json which can also consume that json" in {

      val jason = """{
            "root" : {
              "nested" : {
                "text" : "hello",
                "nil" : null,
                "int" : 2147483647,
                "long" : 9223372036854775807,
                "dec" : 1.23,
                "truthy" : false
              }
            }
            }""".jason

      val schema = SchemaGen(jason)
      withClue(schema.toString(true)) {
        val record = SchemaGen.recordForJson(jason)
        TestData.asJson(record) shouldBe jason
      }
    }
    "generate an avro schema from a number" in {
      val jason  = 123.toString.jason
      val schema = SchemaGen(jason)
      withClue(schema.toString(true)) {
        val record = SchemaGen.recordForJson(jason)
        record.get("value") shouldBe Integer.valueOf(123)
      }
    }
    "generate an avro schema from a boolean" in {
      val jason  = true.toString.jason
      val schema = SchemaGen(jason)
      withClue(schema.toString(true)) {
        val record = SchemaGen.recordForJson(jason)
        record.get("value") shouldBe Boolean.box(true)
      }
    }
    "generate an avro schema from a string" in {
      val jason  = Json.fromString("text")
      val schema = SchemaGen(jason)
      withClue(schema.toString(true)) {
        val record = SchemaGen.recordForJson(jason)
        record.get("value").toString shouldBe "text"
      }
    }

    "generate an avro schema from array json which can also consume that json" in {
      val jason = """{
                "mixarray" : [
                {
                  "foo" : "bar"
                },
                {
                  "foo" : "buzz"
                }
                ]
            }""".jason

      val schema = SchemaGen(jason)
      withClue(schema.toString(true)) {
        val record = SchemaGen.recordForJson(jason)
        TestData.asJson(record) shouldBe jason
      }
    }

    "double-check" ignore {
      val schemaStr = """{
                        |  "type" : "record",
                        |  "name" : "object",
                        |  "namespace" : "gen",
                        |  "doc" : "Created for obj: [mixarray]",
                        |  "fields" : [ {
                        |    "name" : "mixarray",
                        |    "type" : {
                        |      "type" : "array",
                        |      "items" : {
                        |        "type" : "record",
                        |        "name" : "mixarrayType",
                        |        "doc" : "union of mixarrayType and mixarrayType",
                        |        "fields" : [ {
                        |          "name" : "foo",
                        |          "type" : ["null", "int"]
                        |        }, {
                        |          "name" : "fizz",
                        |          "type" : ["null", "int"]
                        |        } ]
                        |      }
                        |    }
                        |  } ]
                        |}
                        |""".stripMargin

      val parser = new Schema.Parser()
      val schema = parser.parse(schemaStr)

      val jason = """{
                "mixarray" : [
                {
                  "foo" : 2
                },
                {
                  "fizz" : 3
                }
                ]
            }""".jason

      val readBack = SchemaGen.recordForJsonAndSchema(jason, schema)

      TestData.asJson(readBack) shouldBe jason
    }
    "generate an avro schema from heterogeneous json arrays which can also consume that json" in {

      val jason = """{
            "root" : {
              "nested" : {
                "text" : "hello",
                "nil" : null,
                "int" : 2147483647,
                "long" : 9223372036854775807,
                "dec" : 1.23,
                "truthy" : false
              },
                "numbers" : [ 1,2,3 ],
                "bools" : [ true, false, true ],
                "strings" : [ "hello", "world" ]
            }
            }""".jason

      val schema = SchemaGen(jason)
      withClue(schema.toString(true)) {
        val record: GenericRecord = SchemaGen.recordForJson(jason)
        TestData.asJson(record) shouldBe jason
      }
    }
  }
}
