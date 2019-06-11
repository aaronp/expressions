package pipelines.reactive

import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}

import scala.util.Try

trait TriggerCallback {
  def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit

  def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_]): Unit

  def onResult(response: Try[TriggerEvent]): Unit

}
object TriggerCallback {

  def apply(onEvent: Try[TriggerEvent] => Unit) = new TriggerCallback {
    override def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit = {}

    override def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_]): Unit = {}

    override def onResult(response: Try[TriggerEvent]): Unit = {
      onEvent(response)
    }
  }

//  type TriggerCallback = Try[TriggerEvent] => Unit

  def Ignore: TriggerCallback = TriggerCallback { _ =>
    ()
  }
}
