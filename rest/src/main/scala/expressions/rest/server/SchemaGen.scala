package expressions.rest.server

import io.circe.{Json, JsonNumber}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.Schema.Type._

import scala.collection.immutable.Map
import scala.jdk.CollectionConverters._

object SchemaGen {

  def apply(record: Json, namespace: String = "gen"): Schema = TypeInst(record).schema(None, namespace)

  sealed abstract class TypeInst(val `type`: Schema.Type) {
    def schema(parentElem: Option[String] = None, namespace: String = "gen"): Schema = this match {
      case ObjType(byName) =>
        val name   = parentElem.getOrElse("object")
        val record = Schema.createRecord(name, s"Created for ${byName.keySet.mkString("obj: [", ",", "]")}", namespace, false)
        val fields = byName.map {
          case (name, inst) => new Schema.Field(name, inst.schema(Option(s"${name}Type"), namespace))
        }
        record.setFields(fields.toList.asJava)
        record
      case values @ ArrayType(_) =>
        val arrayType = values.valueTypes.toList match {
          case Nil =>
            Schema.createUnion(Schema.create(Type.STRING), Schema.create(Type.NULL))
          case List(only) => only.schema(parentElem.map(_ + "Arr"), namespace)
          case many =>
            val types = many.map(_.schema(parentElem.map(_ + "Arr"), namespace))
            Schema.createUnion(types: _*)
        }
        Schema.createArray(arrayType)
      case other => Schema.create(other.`type`)
    }
  }
  object TypeInst {
    def forNumber(json: JsonNumber): TypeInst with NumericType = {
      import json._
      toInt
        .map(IntType.apply)
        .orElse(toLong.map(LongType.apply))
        .getOrElse(DoubleType(toDouble))
    }

    def apply(json: Json): TypeInst = {
      json.fold(
        NullType,
        BoolType.apply,
        forNumber,
        StringType.apply,
        values => ArrayType(values.map(TypeInst.apply)),
        obj => ObjType.fromMap(obj.toMap)
      )
    }
  }
  sealed trait NumericType
  case class DoubleType(value: Double) extends TypeInst(DOUBLE) with NumericType
  case class IntType(value: Int)       extends TypeInst(INT) with NumericType
  case class LongType(value: Long)     extends TypeInst(LONG) with NumericType

  case class BoolType(value: Boolean)               extends TypeInst(BOOLEAN)
  case class StringType(value: String)              extends TypeInst(STRING)
  case class ObjType(values: Map[String, TypeInst]) extends TypeInst(RECORD)
  object ObjType {
    def fromMap(obj: Map[String, Json]): ObjType = new ObjType(obj.view.mapValues(TypeInst.apply).toMap)
  }
  case class ArrayType(values: Vector[TypeInst]) extends TypeInst(ARRAY) {
    def valueTypes: Set[TypeInst] = values.groupBy(_.`type`).values.map(_.head).toSet
  }
  case object NullType extends TypeInst(NULL)
}
