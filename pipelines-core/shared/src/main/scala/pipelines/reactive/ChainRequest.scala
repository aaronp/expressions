package pipelines.reactive

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, ObjectEncoder}
import pipelines.reactive.repo.DataTransform

sealed trait ChainRequest
sealed trait ChainResponse

/**
  * CREATE CHAIN
  */
case class CreateChainRequest(dataSource: String, transforms: Seq[DataTransform], chainName: Option[String] = None) extends ChainRequest
object CreateChainRequest {
  implicit val encoder: ObjectEncoder[CreateChainRequest] = deriveEncoder[CreateChainRequest]
  implicit val decoder: Decoder[CreateChainRequest]       = deriveDecoder[CreateChainRequest]
}
case class CreateChainResponse(pipelineId: String) extends ChainRequest
object CreateChainResponse {
  implicit val encoder: ObjectEncoder[CreateChainResponse] = deriveEncoder[CreateChainResponse]
  implicit val decoder: Decoder[CreateChainResponse]       = deriveDecoder[CreateChainResponse]
}

/**
  * CONNECT SINK
  */
case class ConnectToSinkRequest(pipelineId: String, dataSourceId: Int, dataSink: String) extends ChainRequest
object ConnectToSinkRequest {
  implicit val encoder: ObjectEncoder[ConnectToSinkRequest] = deriveEncoder[ConnectToSinkRequest]
  implicit val decoder: Decoder[ConnectToSinkRequest]       = deriveDecoder[ConnectToSinkRequest]
}

case class ConnectToSinkResponse(dataSource: String) extends ChainResponse
object ConnectToSinkResponse {
  implicit val encoder: ObjectEncoder[ConnectToSinkResponse] = deriveEncoder[ConnectToSinkResponse]
  implicit val decoder: Decoder[ConnectToSinkResponse]       = deriveDecoder[ConnectToSinkResponse]
}
