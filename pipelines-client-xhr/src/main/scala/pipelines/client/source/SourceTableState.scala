package pipelines.client.source

import io.circe.{Decoder, ObjectEncoder}
import pipelines.client.menu.Menu.ItemConfig
import pipelines.client.tables.ClusterizeConfig
import pipelines.client.{Constants, HtmlUtils}

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("SourceTableState")
final case class SourceTableState(name: String, componentName: String, content: Seq[String]) {
  def asLayoutItemConfig(name: String = "Sources"): js.Dynamic = {
    val json = SourceTableState.encoder(this)
    HtmlUtils.log(s"Creating ${json.spaces2} sources table with")
    ItemConfig(name, componentName, json)
  }

  lazy val tableConfig: ClusterizeConfig = {
    ClusterizeConfig(content, scrollId = "sourceTableScrollArea", contentId = "sourceTableContent")
  }
}

object SourceTableState {
  implicit val encoder: ObjectEncoder[SourceTableState] = io.circe.generic.semiauto.deriveEncoder[SourceTableState]
  implicit val decoder: Decoder[SourceTableState]       = io.circe.generic.semiauto.deriveDecoder[SourceTableState]

  def apply(content: Seq[String] = Nil, name: String = "table state") = {
    new SourceTableState(name, Constants.components.sourceTable, content)
  }
}
