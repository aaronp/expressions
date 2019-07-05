package pipelines.reactive

import com.typesafe.scalalogging.StrictLogging
import pipelines.Pipeline
import pipelines.reactive.trigger.{PipelineMatch, TriggerEvent}

import scala.concurrent.{Future, Promise}
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
    private val promise                                      = Promise[TriggerEvent]()
    private val matchPromise                                 = Promise[(PipelineMatch, Pipeline[_, _])]()
    val matchFuture: Future[(PipelineMatch, Pipeline[_, _])] = matchPromise.future
    override def onFailedMatch(input: TriggerInput, mtch: PipelineMatch, err: String): Unit = {
      promise.tryComplete(Try(mtch))
      matchPromise.tryComplete(Failure(new IllegalStateException(s"Triggered match could not create a pipeline: ${err}")))
    }

    override def onMatch(input: TriggerInput, mtch: PipelineMatch, pipeline: Pipeline[_, _]): Unit = {
      promise.tryComplete(Try(mtch))
      matchPromise.tryComplete(Success(mtch -> pipeline))
    }

    override def onResult(response: Try[TriggerEvent]): Unit = {
      promise.tryComplete(response)
      response match {
        case Failure(err)   => matchPromise.tryComplete(Failure(err))
        case Success(other) => matchPromise.tryComplete(Failure(new Exception("Event did not trigger a match:" + other)))
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
