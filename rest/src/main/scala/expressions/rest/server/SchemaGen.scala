package expressions.rest.server

import io.circe.{Json, JsonNumber}
import org.apache.avro.Schema

import scala.collection.immutable.Map

object SchemaGen {

  sealed trait Type
  object Type {
    def forNumber(json: JsonNumber): Type with NumericType = {
      import json._
      toInt
        .map(IntType.apply)
        .orElse(toLong.map(LongType.apply))
        .getOrElse(DoubleType(toDouble))
    }

    def apply(json: Json): Type = {
      json.fold(
        NullType,
        BoolType.apply,
        forNumber,
        StringType.apply,
        values => ArrayType(values.map(Type.apply)),
        obj => ObjType(obj.toMap)
      )
    }
  }
  sealed trait NumericType
  case class DoubleType(value: Double) extends Type with NumericType
  case class IntType(value: Int)       extends Type with NumericType
  case class LongType(value: Long)     extends Type with NumericType

  case class BoolType(value: Boolean)           extends Type
  case class StringType(value: String)          extends Type
  case class ObjType(values: Map[String, Type]) extends Type
  object ObjType {
    def apply(obj: Map[String, Json]): ObjType = new ObjType(obj.view.mapValues(Type.apply).toMap)
  }
  case class ArrayType(values: Vector[Type]) extends Type
  case object NullType                       extends Type


  sealed trait ResolvedType
  object ResolvedType {
    def apply(a : Type, b : Type) = {

    }
  }
  case class Const(resolved : Type)
  case class Union(resolved : Set[Type])

  def apply(record: Json): Schema = {
    record.isNumber
  }
}
