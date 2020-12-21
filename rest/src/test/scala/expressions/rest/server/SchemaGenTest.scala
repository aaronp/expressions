package expressions.rest.server

import io.circe.Json
import io.circe.literal.JsonStringContext
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SchemaGenTest extends AnyWordSpec with Matchers{

  "SchemaGen" should {
    "generate an avro schema from some json which can also consume that json" in {

      val jason =
        json"""{
            "root" : {
              "nested" : {
                "text" : "hello",
                "nil" : null,
                "int" : 2147483647,
                "long" : 9223372036854775807,
                "dec" : 1.23,
                "truthy" : false
              },
                "primarray" : [1, true, "hi"],
                "mixarray" : [
                {
                  "foo" : "bar"
                },
                123,
                {
                  "fizz" : "buzz"
                }
                ]
            }
            }"""

      val schema = SchemaGen(jason)
      println(schema.toString(true))
      withClue(schema.toString(true)) {
        val record = TestData.fromJson[GenericRecord](jason, schema)
        val backAgain = TestData.asJson(record)
        println(backAgain.spaces2)
      }
    }
  }
}
