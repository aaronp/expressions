package pipelines.reactive

import monix.execution.Scheduler
import pipelines.socket.AddressedTextMessage

class PipelineRestService(service: PipelineService, ioScheduler: Scheduler) {

  def getOrCreatePushSource(metadata: Map[String, String]) = {
    implicit val wtf = ioScheduler
    val push         = DataSource.push[AddressedTextMessage](metadata)

    //service.getOrCreateSource(MetadataCriteria(metadata))
  }
  def getOrCreateStringSource(metadata: Map[String, String]) = {
    implicit val wtf = ioScheduler
    val push         = DataSource.push[AddressedTextMessage](metadata)

    //service.getOrCreateSource(MetadataCriteria(metadata))
  }

}
