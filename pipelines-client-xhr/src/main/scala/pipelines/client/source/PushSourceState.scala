package pipelines.client.source

import io.circe.{Decoder, ObjectEncoder}
import pipelines.client.Constants
import pipelines.client.menu.Menu.ItemConfig
import pipelines.reactive.repo.ListedDataSource

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("PushSourceState")
case class PushSourceState(source: ListedDataSource, componentName: String) {
  def asLayoutItemConfig(name: String = "Push Source"): js.Dynamic = {
    ItemConfig(name, componentName, PushSourceState.encoder(this))
  }
}

object PushSourceState {
  implicit val encoder: ObjectEncoder[PushSourceState] = io.circe.generic.semiauto.deriveEncoder[PushSourceState]
  implicit val decoder: Decoder[PushSourceState]       = io.circe.generic.semiauto.deriveDecoder[PushSourceState]

  def apply(): PushSourceState = {
    apply(ListedDataSource(Map.empty, None))
  }

  def apply(source: ListedDataSource): PushSourceState = {
    new PushSourceState(source, Constants.components.pushSource)
  }
}
