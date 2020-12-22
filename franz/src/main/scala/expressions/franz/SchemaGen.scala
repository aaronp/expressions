package expressions.franz

import io.circe.{Json, JsonNumber}
import org.apache.avro.Schema
import org.apache.avro.Schema.Type._
import org.apache.avro.Schema.{Field, Type}
import org.apache.avro.generic.{GenericDatumReader, GenericRecord, IndexedRecord}
import org.apache.avro.io.DecoderFactory

import java.io.{ByteArrayInputStream, DataInputStream}
import java.util
import scala.collection.immutable.Map
import scala.jdk.CollectionConverters._

object SchemaGen {

  def apply(record: Json, namespace: String = "gen"): Schema = TypeInst(record).schema(None, namespace)

  def recordForJson(record: Json, namespace: String = "gen"): GenericRecord = asRecord(record, namespace, 4)

  private def asRecord(record: Json, namespace: String, recursiveCheck: Int): GenericRecord = {
    val inst = TypeInst(record)
    require(recursiveCheck > 0)
    inst.`type` match {
      case RECORD =>
        val schema  = inst.schema(namespace = namespace)
        val decoder = DecoderFactory.get().jsonDecoder(schema, new DataInputStream(new ByteArrayInputStream(record.noSpaces.getBytes())))
        val reader  = new GenericDatumReader[Any](schema)
        reader.read(null, decoder).asInstanceOf[GenericRecord]
      case ARRAY =>
        asRecord(Json.obj("array" -> record), namespace, recursiveCheck - 1)
      case _ =>
        asRecord(Json.obj("value" -> record), namespace, recursiveCheck - 1)
    }
  }

  def recordForJsonAndSchema(record: Json, schema: Schema) = {
    val decoder = DecoderFactory.get().jsonDecoder(schema, new DataInputStream(new ByteArrayInputStream(record.spaces2.getBytes("UTF-8"))))
    val reader  = new GenericDatumReader[GenericRecord](schema)
    reader.read(null, decoder)
  }

  def asJson(record: IndexedRecord): Json = io.circe.parser.parse(record.toString).toTry.get

  def guessType = Schema.createUnion(Schema.create(Type.RECORD), Schema.create(Type.STRING), Schema.create(Type.NULL))

  def asFieldName(name : String): String = name.map {
    case c if c.isLetterOrDigit => c
    case _ => '_'
  }
  sealed abstract class TypeInst(val `type`: Schema.Type) {
    def schema(parentElem: Option[String] = None, namespace: String = "gen"): Schema = this match {
      case ObjType(byName) =>
        val objName = parentElem.getOrElse("object")
        val record  = Schema.createRecord(objName, s"Created for ${byName.keySet.mkString("obj: [", ",", "]")}", namespace, false)
        val fields = byName.map {
          case (name, inst) => new Schema.Field(asFieldName(name), inst.schema(Option(s"${name}Type"), namespace))
        }
        record.setFields(fields.toList.asJava)
        record
      case values @ ArrayType(_) =>
        val arrayType = values.merged(parentElem, namespace)
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

    def merged(parentName: Option[String], namespace: String): Schema = {
      val grouped = values.groupBy(_.`type`)
      grouped.size match {
        // hmm - we're empty. Guess
        case 0 => guessType
        case 1 =>
          grouped.head match {
            case (RECORD, records) => records.map(_.schema(parentName, namespace)).reduce(merge)
            case (ARRAY, records)  => records.map(_.schema(parentName, namespace)).reduce(merge)
            case (simple, _)       => Schema.create(simple)
          }
        case _ =>
          values.map(_.schema(parentName, namespace)).reduce(merge)
      }
    }
  }
  case object NullType extends TypeInst(NULL)

  private def types(s: Schema): Vector[Schema] = {
    if (s.isUnion) {
      s.getTypes.asScala.toVector
    } else {
      Vector(s)
    }
  }

  private def fieldMap(s: Schema) = {
    s.getFields.asScala.groupBy(_.name()).view.mapValues(v => v.ensuring(_.size == 1).head).toMap
  }
  private def mergeStrings(a: String, b: String): String = {
    if (a == b) {
      a
    } else s"$a-and-$b"
  }

  def merge(a: Schema, b: Schema): Schema = {
    val result = if (a.getType == b.getType) {
      // are they both records? we could either merge the records (some fields become nullable)
      // OR just do union type
      if (a.getType == Type.RECORD) {
        val aFields = fieldMap(a)
        val bFields = fieldMap(b)
        val fields: Set[Field] = (aFields.keySet ++ bFields.keySet).map { fieldName =>
          (aFields.get(fieldName), bFields.get(fieldName)) match {
            case (Some(f1), Some(f2)) if f1.schema().getType == f2.schema().getType => new Field(asFieldName(f1.name()), f1.schema(), f1.doc())
            case (Some(f1), Some(f2))                                               => new Field(asFieldName(f1.name()), Schema.createUnion(f1.schema(), f2.schema()), s"Union of $f1 and $f2")
            case (Some(f), _)                                                       => new Field(asFieldName(f.name()), f.schema(), f.doc())
            case (None, Some(f))                                                    => new Field(asFieldName(f.name()), f.schema(), f.doc())
          }
        }
        val name      = mergeStrings(a.getName, b.getName)
        val namespace = mergeStrings(a.getNamespace, b.getNamespace)

        val record                    = Schema.createRecord(name, s"union of ${a.getName} and ${b.getName}", namespace, a.isError || b.isError)
        val jFields: util.List[Field] = fields.toList.asJava
        record.setFields(jFields)
        record
      } else {
        a
      }
    } else {
      Schema.createUnion((types(a) ++ types(b)).distinct.asJava)
    }

    result
  }
}
