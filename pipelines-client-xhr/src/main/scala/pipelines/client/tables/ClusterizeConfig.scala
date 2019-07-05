package pipelines.client.tables

import io.circe.{Decoder, ObjectEncoder}

import scala.scalajs.js
import scala.scalajs.js.JSON

/**
  * {
  * rows: data,
  * scrollId: 'scrollArea',
  * contentId: 'contentArea'
  * }
  */
final case class ClusterizeConfig(rows: Seq[String], scrollId: String, contentId: String) {

  def asJsonDynamic: js.Dynamic = {
    JSON.parse(ClusterizeConfig.encoder(this).noSpaces)
  }
}
object ClusterizeConfig {
  implicit val encoder: ObjectEncoder[ClusterizeConfig] = io.circe.generic.semiauto.deriveEncoder[ClusterizeConfig]
  implicit val decoder: Decoder[ClusterizeConfig]       = io.circe.generic.semiauto.deriveDecoder[ClusterizeConfig]

}
