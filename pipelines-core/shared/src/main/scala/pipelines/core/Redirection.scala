package pipelines.core

import io.circe.{Decoder, ObjectEncoder}

/**
  * Allows us to set redirection
  *
  * @param redirect
  */
case class Redirection(redirect: String) {
  override def toString: String = {
    Redirection.encoder(this).noSpaces
  }
}

object Redirection {
  implicit val encoder: ObjectEncoder[Redirection] = io.circe.generic.semiauto.deriveEncoder[Redirection]
  implicit val decoder: Decoder[Redirection]       = io.circe.generic.semiauto.deriveDecoder[Redirection]
}
