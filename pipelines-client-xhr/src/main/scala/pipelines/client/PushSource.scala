package pipelines.client

import org.scalajs.dom.document

object PushSource {

  def valueOf(id: String): String = {
    HtmlUtils.valueOf(id, document.getElementById(id))
  }

  def cancelPush() = {
    println("cancel push")

  }
  def pushError(dataElemId: String) = {
    println("push error")
  }

  def pushData(toElemId: String, dataElemId: String) = {
    val to   = valueOf(toElemId)
    val data = valueOf(dataElemId)

    PipelinesXhr.listAllTypes()
  }
}
