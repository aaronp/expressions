package pipelines.mongo

import org.mongodb.scala.bson.BsonDocument
import org.scalatest.{Matchers, WordSpec}

class BsonUtilTest extends WordSpec with Matchers {

  def parseNum(bson: BsonDocument) = {
    val got = BsonUtil.fromBson(bson)
    got.get.asObject.get.toMap("number").asNumber.get
  }
  import io.circe.literal._

  "BsonUtil.fromBson" should {
    "convert longs to json" in {
      val bson: BsonDocument = BsonUtil.asDocument(json"""{ "number" : ${Long.MaxValue} }""")
      parseNum(bson).toLong.get shouldBe Long.MaxValue
    }
    "convert doubles to json" in {
      val bson: BsonDocument = BsonUtil.asDocument(json"""{ "number" : ${Double.MaxValue} }""")
      parseNum(bson).toDouble shouldBe Double.MaxValue
    }
    "convert BigDecimals to json" in {
      val expected           = BigDecimal(Long.MaxValue) + 1.23
      val bson: BsonDocument = BsonUtil.asDocument(json"""{ "number" : ${expected} }""")
      val actual             = parseNum(bson).toBigDecimal
      actual shouldBe Some(expected)
    }
    "convert ints to json" in {
      val bson: BsonDocument = BsonUtil.asDocument(json"""{ "number" : ${Int.MaxValue} }""")
      parseNum(bson).toInt shouldBe Some(Int.MaxValue)
    }
  }
}
