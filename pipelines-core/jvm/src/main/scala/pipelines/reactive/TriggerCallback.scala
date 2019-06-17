package pipelines.reactive

import com.typesafe.scalalogging.StrictLogging
import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}

import scala.util.Try

trait TriggerCallback {
  def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit

  def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_, _]): Unit

  def onResult(response: Try[TriggerEvent]): Unit

}
object TriggerCallback {

  class LoggingInstance extends TriggerCallback with StrictLogging {
    override def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit = {
      logger.error(s"onFailedMatch($input, $mtch, $err)")
    }

    override def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_, _]): Unit = {
      logger.error(s"onMatch($input, $mtch, $pipeline)")
    }

    override def onResult(response: Try[TriggerEvent]): Unit = {
      logger.error(s"onResult($response)")
    }
  }

  def apply(onEvent: Try[TriggerEvent] => Unit) = new TriggerCallback {
    override def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit = {}

    override def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_, _]): Unit = {}

    override def onResult(response: Try[TriggerEvent]): Unit = {
      onEvent(response)
    }
  }

//  type TriggerCallback = Try[TriggerEvent] => Unit

  def Ignore: TriggerCallback = TriggerCallback { _ =>
    ()
  }
}
