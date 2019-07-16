package pipelines.reactive

import java.util.UUID

import io.circe.{Decoder, ObjectEncoder}
import pipelines.rest.socket.EventDto

final case class PipelineDto(val matchId: String,
                             val source: EventDto,
                             val steps: Seq[EventDto],
                             sink : EventDto)
object PipelineDto {

  implicit val encoder: ObjectEncoder[PipelineDto] = io.circe.generic.semiauto.deriveEncoder[PipelineDto]
  implicit val decoder: Decoder[PipelineDto] = io.circe.generic.semiauto.deriveDecoder[PipelineDto]

}
