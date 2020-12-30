package expressions.franz

import io.circe.literal.JsonStringContext
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SupportedTypeTest extends AnyWordSpec with Matchers {

  def keyOf(s: Schema) = {
    val schema1 = new AvroSchema(s)
    val b1      = schema1.canonicalString
    val b2      = schema1.schemaType
    val b3      = schema1.references
    (b1, b2, b3)
  }
  "SupportedType" should {
    "produce the same schema for the same types" in {
      val r1: GenericRecord = SchemaGen.recordForJson(json"""{ "key" : "foo", "qualifier" : "y" }""")
      val r2: GenericRecord = SchemaGen.recordForJson(json"""{ "key" : "bar", "qualifier" : "x" }""")
      val k1                = keyOf(r1.getSchema)
      val k2                = keyOf(r2.getSchema)
      k1 shouldBe k2
    }
  }
  "SupportedType.forRecord" should {
    "return a function which will transform ConsumerRecords into json" in {
      ???
    }
  }
}
