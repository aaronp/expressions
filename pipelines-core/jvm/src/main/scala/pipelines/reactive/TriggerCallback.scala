package pipelines.reactive

import com.typesafe.scalalogging.StrictLogging
import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}

import scala.concurrent.Promise
import scala.util.{Failure, Success, Try}

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

  case class PromiseCallback() extends TriggerCallback {
    private val promise      = Promise[TriggerEvent]()
    private val matchPromise = Promise[(PipelineMatch, Pipeline[_, _])]()
    override def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit = {
      promise.complete(Try(mtch))
      matchPromise.complete(Failure(new IllegalStateException(s"Triggered match could not create a pipeline: ${err}")))
    }

    override def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_, _]): Unit = {
      promise.complete(Try(mtch))
      matchPromise.complete(Success(mtch -> pipeline))
    }

    override def onResult(response: Try[TriggerEvent]): Unit = {
      promise.complete(response)
      response match {
        case Failure(err)   => matchPromise.complete(Failure(err))
        case Success(other) => matchPromise.complete(Failure(new Exception("Event did not trigger a match:" + other)))
      }
    }
  }

  def apply(onEvent: Try[TriggerEvent] => Unit): TriggerCallback = new TriggerCallback {
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
