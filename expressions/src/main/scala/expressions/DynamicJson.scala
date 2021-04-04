package expressions

import io.circe.{Decoder, Encoder, Json}

import scala.language.dynamics
import scala.util.Try

/**
  * [[RichJsonPath]] sucks because it needs an implicit Json content.
  */
case class DynamicJson(value: Json) extends Dynamic {

  override def toString: String = asString

  def asBool(default: Boolean = false) = value.asBoolean.getOrElse(default)
  def asString: String                 = value.asString.getOrElse(value.noSpaces)

  def asIntOpt                = value.asNumber.flatMap(_.toInt)
  def asInt(default: Int = 0) = asIntOpt.getOrElse(default)

  def asDoubleOpt: Option[Double]   = value.asNumber.map(_.toDouble)
  def asDouble(default: Double = 0) = asDoubleOpt.getOrElse(default)

  def as[A: Decoder]: Try[A] = value.as[A].toTry

  final def each: Vector[DynamicJson] = {
    value.asArray.getOrElse(Vector.empty).map(DynamicJson.apply)
  }

  final def selectDynamic(field: String): DynamicJson = apply(field)

  def apply(field: String): DynamicJson = {
    val selected = value.asObject.fold(Json.Null) { obj =>
      obj(field).getOrElse(Json.Null)
    }
    DynamicJson(selected)
  }

  final def apply(index: Int): DynamicJson = {
    val selected = value.asArray.fold(Json.Null) { arr =>
      arr.lift(index).getOrElse(Json.Null)
    }
    DynamicJson(selected)
  }
}

object DynamicJson {

  trait LowPriority {
    implicit def asJson(value: DynamicJson): Json                    = value.value
    implicit def asDynamic(json: Json): DynamicJson                  = DynamicJson(json)
    implicit def asVector(json: Json): Vector[DynamicJson]           = asDynamic(json).each
    implicit def asVector(dynamic: DynamicJson): Vector[DynamicJson] = dynamic.value.asArray.map(_.map(DynamicJson.apply)).getOrElse(Vector(dynamic))
  }

  implicit val encoder: Encoder[DynamicJson] = Encoder[DynamicJson](_.value)
  implicit val decoder: Decoder[DynamicJson] = Decoder.decodeJson.map(DynamicJson.apply)

  object implicits extends LowPriority {
    implicit class Syntax(val json: Json) extends AnyVal {
      def asDynamic = DynamicJson(json)
    }
  }
}
