package pipelines

import java.util.UUID

import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import pipelines.reactive.{DataSink, DataSource, Transform}

import scala.util.control.NonFatal

class Pipeline[A] private (val matchId: UUID,
                           val root: DataSource,
                           val logicalSource: DataSource,
                           val transformsByIndex: Map[Int, Transform],
                           val sink: DataSink,
                           scheduler: Scheduler,
                           val result: CancelableFuture[A]) {
  def cancel(): Unit = result.cancel()
}

object Pipeline {

  def from[In, Out](id: UUID, source: DataSource, transforms: Seq[Transform], sink: DataSink.Aux[In, Out])(implicit scheduler: Scheduler): Either[String, Pipeline[Out]] = {
    apply(id, source, transforms, sink)(identity)
  }

  def apply[In, Out](id: UUID, source: DataSource, transforms: Seq[Transform], sink: DataSink.Aux[In, Out])(prepare: Observable[In] => Observable[In])(
      implicit scheduler: Scheduler): Either[String, Pipeline[sink.Output]] = {

    connect(source, transforms) match {
      case Right(logicalSource) =>
        if (logicalSource.contentType.matches(sink.inputType)) {
          try {
            val obs: Observable[In]                   = prepare(logicalSource.asObservable.asInstanceOf[Observable[In]])
            val future: CancelableFuture[sink.Output] = sink.connect(logicalSource.contentType, obs, logicalSource.metadata)
            val byIndex                               = transforms.zipWithIndex.map(_.swap).toMap
            Right(new Pipeline[sink.Output](id, source, logicalSource, byIndex, sink, scheduler, future))
          } catch {
            case NonFatal(e) =>
              Left(s"Error connecting $source with $sink: $e")
          }
        } else {
          Left(s"Can't connect ${logicalSource.contentType} with ${sink.inputType}")
        }

      case Left(err) => Left(err)
    }
  }

  def typesMatch(source: DataSource, transforms: Seq[Transform], sink: DataSink): Boolean = {
    connect(source, transforms).exists { newDs =>
      newDs.contentType.matches(sink.inputType)
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
