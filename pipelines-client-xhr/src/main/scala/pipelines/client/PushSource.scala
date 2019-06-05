package pipelines.client

import pipelines.client.PipelinesApp.valueOf

object PushSource {

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
