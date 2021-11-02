package expressions.franz

import org.apache.avro.Schema
import org.apache.avro.Schema.Type.*
import io.circe.Json

import scala.collection.mutable

object DataGen {

  import scala.jdk.CollectionConverters.{given, *}

  extension(x: Long) {
    def isOdd = x % 2 == 0
  }

  case class Gen(seed: Long) {
    def string: String = eie.io.AlphaCounter.from(seed).next()
    def long: Long     = seed
    def int: Int       = seed.toInt
    def float: Float   = seed.toFloat
    def double: Double = seed.toDouble
    def bool: Boolean  = (seed % 7).isOdd
  }

  object Gen {
    def forSeed(seed: Seed) = seed.next -> Gen(seed.long)
  }

  extension[X, Y](pear: (X, Y)) {
    def map[A](f: Y => A): (X, A) = (pear._1, f(pear._2))
  }

  def recordForSchema(schema: Schema, seed: Seed = Seed(), gen: Seed => (Seed, Gen) = Gen.forSeed): (Seed, Json) = {
    schema.getType match {
      case RECORD =>
        val pear = schema.getFields.asScala.foldLeft(seed -> List[(String, Json)]()) {
          case ((s, fields), field) =>
            val (next, json) = recordForSchema(field.schema(), s, gen)
            (next, (field.name(), json) :: fields)
        }
        pear.map { fields =>
          Json.obj(fields.toArray *)
        }

      case ENUM =>
        val symbols = schema.getEnumSymbols
        val size    = symbols.size()
        val index   = seed.int(size - 1)
        val s       = symbols.get(index)
        seed -> Json.fromString(s)
      case ARRAY =>
        val (newSeed1, a) = recordForSchema(schema.getElementType, seed, gen)
        val (newSeed2, b) = recordForSchema(schema.getElementType, newSeed1, gen)
        newSeed2 -> Json.arr(a, b)
      case MAP =>
        val (s1, name1) = gen(seed)
        val (s2, name2) = gen(s1)
        val (s3, a)     = recordForSchema(schema.getValueType, s2, gen)
        val (s4, b)     = recordForSchema(schema.getValueType, s3, gen)
        s4 -> Json.obj(name1.string -> a, name2.string -> b)
      case UNION =>
        val nonNull = schema.getTypes.asScala.filterNot(_.isNullable).headOption
        nonNull.fold(seed -> Json.Null)(recordForSchema(_, seed, gen))
      case FIXED   => gen(seed).map(g => Json.fromInt(g.int))
      case STRING  => gen(seed).map(g => Json.fromString(g.string))
      case BYTES   => gen(seed).map(g => Json.arr(Json.fromInt(g.int)))
      case INT     => gen(seed).map(g => Json.fromInt(g.int))
      case LONG    => gen(seed).map((g: Gen) => Json.fromLong(g.long))
      case FLOAT   => gen(seed).map((g: Gen) => Json.fromFloat(g.float).getOrElse(Json.fromLong(g.long)))
      case DOUBLE  => gen(seed).map((g: Gen) => Json.fromDouble(g.double).getOrElse(Json.fromLong(g.long)))
      case BOOLEAN => gen(seed).map((g: Gen) => Json.fromBoolean(g.bool))
      case NULL    => (seed, Json.Null)
    }
  }
}
