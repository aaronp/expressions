package expressions

import io.circe.optics.JsonObjectOptics._
import io.circe.optics.JsonOptics.{
  jsonArray,
  jsonBigDecimal,
  jsonBigInt,
  jsonBoolean,
  jsonByte,
  jsonDescendants,
  jsonDouble,
  jsonInt,
  jsonLong,
  jsonNull,
  jsonNumber,
  jsonObject,
  jsonShort,
  jsonString
}
import io.circe.optics.{JsonFoldPath, JsonPath, JsonTraversalPath, UnsafeOptics}
import io.circe._
import monocle.Optional
import monocle.function.{At, FilterIndex, Index}

import scala.language.dynamics

final class RichDynamicJson(val jsonValue: Json) extends Dynamic {

  override def toString: String = jsonValue.noSpaces

  private def json: Optional[Json, Json] = Optional[Json, Json](_ => Option(jsonValue)) { _ => _ =>
    jsonValue
  }

  final def `null`: Optional[Json, Unit]           = json.composePrism(jsonNull)
  final def boolean: Optional[Json, Boolean]       = json.composePrism(jsonBoolean)
  final def byte: Optional[Json, Byte]             = json.composePrism(jsonByte)
  final def short: Optional[Json, Short]           = json.composePrism(jsonShort)
  final def int: Optional[Json, Int]               = json.composePrism(jsonInt)
  final def long: Optional[Json, Long]             = json.composePrism(jsonLong)
  final def bigInt: Optional[Json, BigInt]         = json.composePrism(jsonBigInt)
  final def double: Optional[Json, Double]         = json.composePrism(jsonDouble)
  final def bigDecimal: Optional[Json, BigDecimal] = json.composePrism(jsonBigDecimal)
  final def number: Optional[Json, JsonNumber]     = json.composePrism(jsonNumber)
  final def string: Optional[Json, String]         = json.composePrism(jsonString)
  final def arr: Optional[Json, Vector[Json]]      = json.composePrism(jsonArray)
  final def obj: Optional[Json, JsonObject]        = json.composePrism(jsonObject)

  final def at(field: String): Optional[Json, Option[Json]] =
    json.composePrism(jsonObject).composeLens(At.at(field))

  final def selectDynamic(field: String): JsonPath =
    JsonPath(json.composePrism(jsonObject).composeOptional(Index.index(field)))

  final def applyDynamic(field: String)(index: Int): JsonPath = selectDynamic(field).index(index)

  final def apply(i: Int): JsonPath = index(i)

  final def index(i: Int): JsonPath =
    JsonPath(json.composePrism(jsonArray).composeOptional(Index.index(i)))

  final def each: JsonTraversalPath =
    JsonTraversalPath(json.composeTraversal(jsonDescendants))

  final def filterByIndex(p: Int => Boolean): JsonTraversalPath =
    JsonTraversalPath(arr.composeTraversal(FilterIndex.filterIndex(p)))

  final def filterByField(p: String => Boolean): JsonTraversalPath =
    JsonTraversalPath(obj.composeTraversal(FilterIndex.filterIndex(p)))

  final def filterUnsafe(p: Json => Boolean): JsonPath =
    JsonPath(json.composePrism(UnsafeOptics.select(p)))

  final def filter(p: Json => Boolean): JsonFoldPath =
    JsonFoldPath(filterUnsafe(p).json.asFold)

  final def as[A](implicit decode: Decoder[A], encode: Encoder[A]): Optional[Json, A] =
    json.composePrism(UnsafeOptics.parse)

}
