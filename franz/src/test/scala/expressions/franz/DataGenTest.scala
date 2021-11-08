package expressions.franz

import java.util.Base64

class DataGenTest extends BaseFranzTest {
  "DataGen" should {
    "generate data from a schema" in {
      val record = DataGen.forSchema(Schemas.exampleSchema)
      val Right(name) = record.hcursor.downField("addresses").downN(0).downField("name").as[String]
      name should not be(empty)
    }
  }
}
