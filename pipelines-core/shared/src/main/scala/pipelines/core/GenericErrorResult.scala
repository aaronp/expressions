package pipelines.core

import io.circe.{Decoder, ObjectEncoder}

final case class GenericErrorResult(message: String, details: Seq[String] = Nil) extends Exception(message) {
  def description: String = details match {
    case Seq() => message
    case other => other.mkString(s"$message:", ", ", "")
  }
}
object GenericErrorResult {
  implicit val encoder: ObjectEncoder[GenericErrorResult] = io.circe.generic.semiauto.deriveEncoder[GenericErrorResult]
  implicit val decoder: Decoder[GenericErrorResult]       = io.circe.generic.semiauto.deriveDecoder[GenericErrorResult]

}
