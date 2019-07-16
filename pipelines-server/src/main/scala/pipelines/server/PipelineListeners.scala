package pipelines.server

import com.typesafe.scalalogging.StrictLogging
import pipelines.reactive._

object PipelineListeners extends StrictLogging {

  /**
    * A place to set-up some sources, sinks and triggers specific to this application
    *
    * @param pipelinesService
    * @return
    */
  def apply(pipelinesService: PipelineService): Unit = {
    SubscribeOnMatchSink.listenToNewSocketSources(pipelinesService)
  }

}
