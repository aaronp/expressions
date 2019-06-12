package pipelines

import java.util.UUID

import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import pipelines.reactive.{DataSink, DataSource, Transform}

import scala.util.control.NonFatal

class Pipeline[In, A] private (val matchId: UUID,
                               val root: DataSource,
                               val logicalSource: DataSource,
                               val steps: Seq[Pipeline.ChainStep],
                               val sink: DataSink.Aux[In, A],
                               scheduler: Scheduler,
                               val obs: Observable[In]) {

  val result: CancelableFuture[sink.Output] = sink.connect(logicalSource.contentType, obs, logicalSource.metadata)(scheduler)

  def cancel(): Unit = result.cancel()
}

object Pipeline {

  def from[In, Out](id: UUID, source: DataSource, transforms: Seq[(String, Transform)], sink: DataSink.Aux[In, Out])(
      implicit scheduler: Scheduler): Either[String, Pipeline[In, Out]] = {
    apply(id, source, transforms, sink)(identity)
  }

  def apply[In, Out](id: UUID, source: DataSource, transforms: Seq[(String, Transform)], sink: DataSink.Aux[In, Out])(prepare: Observable[In] => Observable[In])(
      implicit scheduler: Scheduler): Either[String, Pipeline[In, sink.Output]] = {

    ChainStep.connect(source, transforms) match {
      case Right(steps) =>
        val logicalSource = steps.last.source
        if (logicalSource.contentType.matches(sink.inputType)) {
          try {
            val obs: Observable[In] = prepare(logicalSource.asObservable.asInstanceOf[Observable[In]])
            Right(new Pipeline[In, sink.Output](id, source, logicalSource, steps, sink, scheduler, obs))
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

  sealed trait ChainStep {
    def source: DataSource
  }
  object ChainStep {

    /** @param source the initial data source
      * @param transforms the transformations to apply
      * @return either an error or a 'logical' source which encapsulates all the transforms
      */
    def connect(source: DataSource, transforms: Seq[(String, Transform)]): Either[String, Seq[ChainStep]] = {
      val (chainSeq, errorOpt) = transforms.foldLeft(Seq[ChainStep](Root(source)) -> Option.empty[String]) {
        case ((chain @ head +: _, None), (name, t)) =>
          t.applyTo(head.source) match {
            case Some(next) => (Step(next, name, t) +: chain) -> None
            case None =>
              val err = s"'$t' can't be applied to '${head.source.contentType}'"
              chain -> Option(err)
          }
        case (none, _) => none
      }
      errorOpt match {
        case None => Right(chainSeq.reverse)
        case Some(err) =>
          val types = chainSeq.reverse.map(_.source.contentType)
          val msg =
            s"Can't connect source with type ${source.contentType} through ${transforms.size} transforms as the types don't match: ${types.mkString("->")}: $err"
          Left(msg)
      }
    }
  }
  case class Root(override val source: DataSource)                                     extends ChainStep
  case class Step(override val source: DataSource, name: String, transform: Transform) extends ChainStep
}
