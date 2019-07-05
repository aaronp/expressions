package pipelines.reactive.repo

import cats.syntax.functor._
import io.circe.{Decoder, Encoder, ObjectEncoder}
import pipelines.reactive.ContentType

sealed trait PushSourceResponse
object PushSourceResponse {
  import io.circe.syntax._
  implicit val encoder = Encoder.instance[PushSourceResponse] {
    case value: CreatedPushSourceResponse => value.asJson
    case value: PushedValueResponse       => value.asJson
  }

  implicit val decoder: Decoder[PushSourceResponse] = {
    List[Decoder[PushSourceResponse]](
      Decoder[CreatedPushSourceResponse].widen,
      Decoder[PushedValueResponse].widen
    ).reduceLeft(_ or _)
  }
}

final case class CreatedPushSourceResponse(name: String, contentType: ContentType, metadata: Map[String, String]) extends PushSourceResponse

object CreatedPushSourceResponse {
  implicit val encoder: ObjectEncoder[CreatedPushSourceResponse] = io.circe.generic.semiauto.deriveEncoder[CreatedPushSourceResponse]
  implicit val decoder: Decoder[CreatedPushSourceResponse]       = io.circe.generic.semiauto.deriveDecoder[CreatedPushSourceResponse]
}
final case class PushedValueResponse(ok: Boolean) extends PushSourceResponse
object PushedValueResponse {
  implicit val encoder: ObjectEncoder[PushedValueResponse] = io.circe.generic.semiauto.deriveEncoder[PushedValueResponse]
  implicit val decoder: Decoder[PushedValueResponse]       = io.circe.generic.semiauto.deriveDecoder[PushedValueResponse]

}
