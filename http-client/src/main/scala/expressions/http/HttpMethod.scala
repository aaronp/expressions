package expressions.http

import io.circe.{Decoder, Encoder}

sealed trait HttpMethod {
  def name: String
}

object HttpMethod {
  abstract class Base protected (val name: String) {
    override def toString: String = name
    override def equals(obj: Any): Boolean = obj match {
      case impl: Base => name == impl.name
      case _          => false
    }
  }
  case object GET     extends Base("GET") with HttpMethod
  case object POST    extends Base("POST") with HttpMethod
  case object PUT     extends Base("PUT") with HttpMethod
  case object DELETE  extends Base("DELETE") with HttpMethod
  case object HEAD    extends Base("HEAD") with HttpMethod
  case object OPTIONS extends Base("OPTIONS") with HttpMethod

  def values: Set[HttpMethod] = Set(
    GET,
    POST,
    PUT,
    DELETE,
    HEAD,
    OPTIONS
  )

  lazy val byName: Map[String, HttpMethod]  = values.map(m => (m.name, m)).toMap
  implicit val encoder: Encoder[HttpMethod] = Encoder.encodeString.contramap[HttpMethod](_.name)
  implicit val decoder: Decoder[HttpMethod] = Decoder.decodeString.emap { key =>
    byName.get(key) match {
      case None    => Left(values.mkString("Expected one of [", ",", "]"))
      case Some(m) => Right(m)
    }
  }
}
