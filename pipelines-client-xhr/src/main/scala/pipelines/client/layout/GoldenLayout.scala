package pipelines.client.layout

import org.scalajs.dom.Node

import scala.scalajs.js

@js.native
trait GoldenLayout extends js.Object {

  var _components                                         = js.Object
  def root: GoldenLayoutRoot                              = js.native
  def createDragSource(e: Node, config: js.Dynamic): Unit = js.native
  var selectedItem: RowOrColumn                           = js.native
}
