package pipelines.core

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class GenericMessageResult(message: String, details: Seq[String] = Nil) {
  def description: String = details match {
    case Seq() => message
    case other => other.mkString(s"$message:", ", ", "")
  }
}
object GenericMessageResult {

  implicit val encoder = deriveEncoder[GenericMessageResult]
  implicit val decoder = deriveDecoder[GenericMessageResult]
}
