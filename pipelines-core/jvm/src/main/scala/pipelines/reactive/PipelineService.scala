package pipelines.reactive

import java.util.UUID

import monix.execution.Scheduler
import monix.reactive.{Observable, Observer, Pipe}
import pipelines.reactive.PipelineService.{DataSourceAdded, Event}

import scala.util.{Failure, Try}

/**
  * The implimentation of querying sources/sinks/transformations
  */
class PipelineService(initialRepo: SourceRepository, eventSource: Observer[Event], val events: Observable[Event])(implicit sched: Scheduler) extends PipelineService {

  private var repo       = initialRepo
  private var chainsById = Map[String, DataChain]()
  private object Lock

  def createPipeline(request: CreateChainRequest): Either[String, CreateChainResponse] = {
    val either: Either[String, DataChain] = repo.createChain(request)
    either.right.map { chain =>
      val id = UUID.randomUUID.toString
      Lock.synchronized {
        chainsById = chainsById.updated(id, chain)
      }
      CreateChainResponse(id)
    }
  }

  def connectToSink(request: ConnectToSinkRequest): Try[ConnectToSinkResponse] = {
    val found = Lock.synchronized {
      chainsById.get(request.pipelineId)
    }

    found match {
      case Some(chain) =>
        repo.sinksByName.get(request.dataSink) match {
          case Some(sink) =>
            val success = chain.connect(request.dataSourceId)(sink.connect)
            success.map { result =>
              ConnectToSinkResponse(request.dataSourceId.toString)
            }
          case None =>
            Failure(new Exception(s"Couldn't find sink '${request.dataSink}'"))
        }

      case None => Failure(new Exception(s"Couldn't find '${request.pipelineId}'"))
    }
  }

  def registerDataSource(name: String, dataSource: DataSource): Option[SourceRepository] = {
    Lock.synchronized {
      repo.addSource(name, dataSource).map { newRepo =>
        eventSource.onNext(DataSourceAdded(name, dataSource))
        repo = newRepo
        newRepo
      }
    }
  }

}

object PipelineService {

  sealed trait Event
  case class DataChainCreated(newChain: DataChain)               extends Event
  case class DataSourceAdded(name: String, newChain: DataSource) extends Event

  def apply(repo: SourceRepository)(implicit sched: Scheduler): PipelineService = {
    val (input: Observer[Event], output: Observable[Event]) = Pipe.replayLimited[Event](10).multicast

    new PipelineService(repo, input, output)
  }

}
