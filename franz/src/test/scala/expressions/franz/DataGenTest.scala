package expressions.franz

import java.util.Base64

class DataGenTest  extends BaseFranzTest {
  "DataGen" should {
    "generate data from a schema" in {
      val record = DataGen.recordForSchema(Schemas.exampleSchema)
      println(record)
    }
  }
}
