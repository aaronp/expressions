package pipelines.client.layout

import scala.scalajs.js
@js.native
trait RowOrColumn extends js.Object {
  def addChild(newItemConfig: js.Dynamic) = js.native

  def contentItems: Array[RowOrColumn] = js.native
  def config: js.Dynamic               = js.native
}
