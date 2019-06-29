package pipelines.mongo

import io.circe
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.bson.BsonNumber
import org.bson.json.{JsonMode, JsonWriterSettings}
import org.bson.types.{Decimal128, ObjectId}
import org.mongodb.scala.Document
import org.mongodb.scala.bson.collection._
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonString}

import scala.util.Try

/**
  * TODO find or create a library which maps BsonValues to circe json types
  */
object BsonUtil {
  def asDocument[A: Encoder](value: A): BsonDocument = asBson(value.asJson).asInstanceOf[BsonDocument]

  def asBson(json: Json): org.bson.BsonValue = {
    import scala.collection.JavaConverters._
    json.fold[org.bson.BsonValue](
      org.bson.BsonNull.VALUE,
      org.bson.BsonBoolean.valueOf,
      (jsonNum: JsonNumber) => numAsBson(jsonNum),
      jsonString => new BsonString(jsonString),
      jsonArray => new BsonArray(jsonArray.map(asBson).asJava),
      (jsonObject: JsonObject) => {
        val bsonMap = jsonObject.toMap.mapValues(asBson)
        BsonDocument(bsonMap)
      }
    )
  }

  def numAsBson(jsonNum: JsonNumber): BsonNumber = {
    val intOpt  = jsonNum.toInt.map(x => new org.bson.BsonInt32(x))
    def longOpt = jsonNum.toLong.map(x => new org.bson.BsonInt64(x))
    def decOpt = jsonNum.toBigDecimal.map { x: BigDecimal =>
      val one28: Decimal128 = new Decimal128(x.bigDecimal)
      new org.bson.BsonDecimal128(one28)
    }
    intOpt.orElse(longOpt).orElse(decOpt).getOrElse {
      val one28: Decimal128 = new Decimal128(BigDecimal(jsonNum.toDouble).bigDecimal)
      new org.bson.BsonDecimal128(one28)
    }
  }

  def asMutableDocument(json: Json): mutable.Document = mutable.Document(json.noSpaces)

  def parse[A: Decoder](doc: immutable.Document): Either[circe.Error, A] = {
    decode[A](doc.toJson())
  }

  private def cleanBsonNums(obj: JsonObject): Json = {
    def convert(value: Json) = {
      val numOpt = value.asNumber.map(Json.fromJsonNumber)
      numOpt.getOrElse {
        val numberWang = value.asString.map(args4c.unquote).get
        if (numberWang.contains(".")) {
          Json.fromBigDecimal(BigDecimal(numberWang).bigDecimal)
        } else {
          Json.fromLong(numberWang.toLong)
        }
      }
    }
    obj.size match {
      case 1 =>
        obj.toMap.head match {
          case ("$numberDecimal", value) => convert(value)
          case ("$numberLong", value)    => convert(value)
          case (name, value)             => Json.obj(name -> sanitizeFromBson(value))
        }
      case _ => Json.fromJsonObject(obj.mapValues(sanitizeFromBson))
    }
  }

  /**
    * BSON will read back numeric values as:
    * {{{
    *   {
    *    "aLong" : {
    *      "$numberLong" : "123"
    *    }
    *   }
    * }}}
    *
    * Which sucks. This hack turns it into:
    * {{{
    *   {
    *    "aLong" : "123"
    *   }
    * }}}
    *
    * @param bsonValue
    * @return
    */
  def sanitizeFromBson(bsonValue: Json): Json = {
    bsonValue.fold[Json](
      Json.Null,
      Json.fromBoolean,
      Json.fromJsonNumber,
      Json.fromString,
      arr => Json.arr(arr: _*),
      cleanBsonNums
    )
  }

  private val bsonJsonSettings = JsonWriterSettings.builder.outputMode(JsonMode.RELAXED).build
  def idForDocument(doc: Document): String = {
    val oid: ObjectId = doc.getObjectId("_id")
    oid.toHexString
  }
  def fromBson(doc: Document): Try[Json] = {
    val jsonStr: String = doc.toJson(bsonJsonSettings)
    fromBson(jsonStr)
  }

  def fromBson(bson: String): Try[Json] = {
    io.circe.parser.decode[Json](bson).toTry.map(sanitizeFromBson)
  }
}
