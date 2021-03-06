package expressions

import io.circe.{Json, JsonNumber}
import monocle.Optional

class RichOptional[A](val optional: Optional[Json, A]) {

  def =====(other: Optional[Json, A])(implicit json: Json): Boolean = {
    val lhs: Option[A] = optional.getOption(json)
    val rhs            = other.getOption(json)
    lhs == rhs
  }

  def =====(expected: String)(implicit json: Json): Boolean = {
    optional.getOption(json).exists {
      case str: String  => str == expected
      case actual: Json => actual.asString.exists(_ == expected)
      case _            => false
    }
  }

  def =====(expected: Int)(implicit json: Json): Boolean = {
    val actual = optional.getOption(json)
    actual.exists {
      case num: JsonNumber => num.toInt.exists(_ == expected)
      case num: Json       => num.asNumber.flatMap(_.toInt).exists(_ == expected)
      case _               => false
    }
  }
  def =====(expected: Long)(implicit json: Json): Boolean = {
    val actual = optional.getOption(json)
    actual.exists {
      case num: JsonNumber => num.toLong.exists(_ == expected)
      case num: Json       => num.asNumber.flatMap(_.toLong).exists(_ == expected)
      case _               => false
    }
  }

  def =====(expected: Double)(implicit json: Json): Boolean = compareDouble(_ == expected)
  def >=(expected: Double)(implicit json: Json): Boolean    = compareDouble(_ >= expected)
  def >(expected: Double)(implicit json: Json): Boolean     = compareDouble(_ > expected)
  def <=(expected: Double)(implicit json: Json): Boolean    = compareDouble(_ <= expected)
  def <(expected: Double)(implicit json: Json): Boolean     = compareDouble(_ < expected)

  def get(implicit json: Json)              = value.getOrElse(sys.error(s"Couldn't resolve for $json"))
  def value(implicit json: Json): Option[A] = optional.getOption(json)

  private def compareDouble(compare: Double => Boolean)(implicit json: Json): Boolean = {
    value.exists {
      case num: JsonNumber => compare(num.toDouble)
      case num: Json       => num.asNumber.map(_.toDouble).exists(compare)
      case _               => false
    }
  }
}
