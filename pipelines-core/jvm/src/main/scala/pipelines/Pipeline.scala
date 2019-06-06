package pipelines

import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import pipelines.reactive.{DataSink, DataSource, Transform}

import scala.util.control.NonFatal

class Pipeline[A] private (root: DataSource,
                           logicalSource: DataSource,
                           transformsByIndex: Map[Int, Transform],
                           sink: DataSink,
                           scheduler: Scheduler,
                           connection: CancelableFuture[A]) {
  def cancel(): Unit = connection.cancel()
}

object Pipeline {
//  def apply(pipelineMatch: PipelineMatch)(prepare : Observable[In] => Observable[pipelineMatch.sink.Input])(implicit scheduler: Scheduler): Either[String, Pipeline[DataSink#Output]] = {
//
//  }

  def apply[In, Out](source: DataSource, transforms: Seq[Transform], sink: DataSink.Aux[In, Out])(prepare: Observable[In] => Observable[In])(
      implicit scheduler: Scheduler): Either[String, Pipeline[sink.Output]] = {
    connect(source, transforms) match {
      case Right(logicalSource) =>
        if (logicalSource.contentType.matches(sink.contentType)) {
          try {
            val obs: Observable[In]                   = prepare(logicalSource.asObservable.asInstanceOf[Observable[In]])
            val future: CancelableFuture[sink.Output] = sink.connect(obs)
            val byIndex                               = transforms.zipWithIndex.map(_.swap).toMap
            Right(new Pipeline[sink.Output](source, logicalSource, byIndex, sink, scheduler, future))
          } catch {
            case NonFatal(e) =>
              Left(s"Error connecting $source with $sink: $e")
          }
        } else {
          Left(s"Can't connect ${logicalSource.contentType} with ${sink.contentType}")
        }

      case Left(err) => Left(err)
    }
  }

  def typesMatch(source: DataSource, transforms: Seq[Transform], sink: DataSink): Boolean = {
    connect(source, transforms).exists { newDs =>
      newDs.contentType.matches(sink.contentType)
    }
  }

  def connect(source: DataSource, transforms: Seq[Transform]): Either[String, DataSource] = {
    val (chainedSourceOpt, types) = transforms.foldLeft(Option(source) -> Seq[String](source.contentType.toString)) {
      case ((Some(src), chain), t) =>
        t.applyTo(src) match {
          case entry @ Some(next) => entry -> (chain :+ next.contentType.toString)
          case None =>
            val err = s" !ERROR! '$t' can't be applied to '${src.contentType}'"
            None -> (chain :+ err)
        }
      case (none, _) => none
    }
    chainedSourceOpt match {
      case Some(ok) => Right(ok)
      case None =>
        val msg =
          s"Can't connect source with type ${source.contentType} through ${transforms.size} transforms as the types don't match: ${types.mkString("->")}"
        Left(msg)
    }
  }
}
