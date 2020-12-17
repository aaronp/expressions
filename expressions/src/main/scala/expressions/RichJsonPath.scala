package expressions

import io.circe.optics.JsonPath
import io.circe.{Decoder, Json}
import monocle.{Optional, Traversal}

/**
  * Allows us to call comparison (et al) operation on a JsonPath, so that our scripted expressions can make the json implicit, and thus just
  *
  * {{{
  *   it.path.to.a.value <= 4
  * }}}
  *
  * looks like we're just chaining calls.
  *
  * @param path
  */
class RichJsonPath(val path: JsonPath) extends AnyVal {

  import implicits._

  def asJsonString(implicit json: Json)           = get[Json].noSpaces
  def get[A: Decoder](implicit json: Json)        = valueOpt.getOrElse(sys.error(s"Json path didn't resolve for $json")).as[A].toTry.get
  def valueOpt(implicit json: Json): Option[Json] = path.json.getOption(json)

  def map[A](thunk: RichDynamicJson => Optional[Json, A])(implicit jsonValue: Json): Seq[A] = {
    val all: Seq[Json] = path.each.json.getAll(jsonValue)
    all.flatMap { j =>
      thunk(new RichDynamicJson(j)).getOption(j)
    }
  }
  def mapAs[A](thunk: RichDynamicJson => A)(implicit jsonValue: Json): Seq[A] = {
    val all: Seq[Json] = path.each.json.getAll(jsonValue)
    all.map { j =>
      thunk(new RichDynamicJson(j))
    }
  }

  def flatMapOpt[A](thunk: RichDynamicJson => Iterable[Optional[Json, A]])(implicit jsonValue: Json): Seq[A] = {
    path.each.json.getAll(jsonValue).flatMap { j =>
      val results = thunk(new RichDynamicJson(j))
      results.flatMap { r =>
        r.getOption(j)
      }
    }
  }
  def flatMapSeq[A](thunk: RichDynamicJson => Iterable[A])(implicit jsonValue: Json): Seq[A] = {
    path.each.json.getAll(jsonValue).flatMap { j =>
      val results = thunk(new RichDynamicJson(j))
      results.flatMap {
        case o: Optional[Json, _] => o.getOption(j).map(_.asInstanceOf[A])
        case a                    => Some(a)
      }
    }
  }
  def flatMap[A](thunk: RichDynamicJson => Traversal[Json, A])(implicit jsonValue: Json): Seq[A] = {
    path.each.json.getAll(jsonValue).flatMap { j =>
      val traversal = thunk(new RichDynamicJson(j))
      traversal.getAll(j)
    }
  }

  /**
    * The normal double equals "==" gets turning into one of these bad-boys so as not to overload normal equality
    * @param other
    * @param json
    * @return
    */
  def =====(other: JsonPath)(implicit json: Json): Boolean = {
    // TODO: other types
    path.number.=====(other.number) ||
    path.string.=====(other.string)
  }

  def asIntOpt(implicit json: Json): Option[Int] = path.number.getOption(json).flatMap(_.toInt)
  def asInt(implicit json: Json): Int            = asIntOpt.getOrElse(Int.MinValue)

  def asLongOpt(implicit json: Json): Option[Long] = path.number.getOption(json).flatMap(_.toLong)
  def asLong(implicit json: Json): Long            = asLongOpt.getOrElse(Long.MinValue)

  def asDoubleOpt(implicit json: Json): Option[Double] = path.number.getOption(json).map(_.toDouble)
  def asDouble(implicit json: Json): Double            = asDoubleOpt.getOrElse(Double.MinValue)

  def asBooleanOpt(implicit json: Json): Option[Boolean] = path.boolean.getOption(json)
  def asBoolean(implicit json: Json): Boolean            = asBooleanOpt.getOrElse(false)

  def asStringOpt(implicit json: Json): Option[String] = path.string.getOption(json)
  def asString(implicit json: Json): String            = asStringOpt.getOrElse("")

  def =====(value: String)(implicit json: Json): Boolean = path.string.=====(value)
  def =====(value: Double)(implicit json: Json): Boolean = path.number.=====(value)
  def =====(value: Long)(implicit json: Json): Boolean   = path.string.=====(value)
  def <=(value: Double)(implicit json: Json): Boolean    = path.number.<=(value)
  def >=(value: Double)(implicit json: Json): Boolean    = path.number.>=(value)
  def >(value: Double)(implicit json: Json): Boolean     = path.number.>(value)
  def <(value: Double)(implicit json: Json): Boolean     = path.number.<(value)

}

object RichJsonPath {
  implicit def backToPath(rich: RichJsonPath): JsonPath = rich.path
}
