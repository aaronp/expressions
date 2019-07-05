package pipelines

import java.util.UUID

import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.Observable
import pipelines.reactive.{ContentType, DataSink, DataSource, Transform}

import scala.util.control.NonFatal

/** Represents a connect source w/ a sink (view some transformation (chain) steps)
  *
  * @param matchId
  * @param root
  * @param logicalSource
  * @param steps
  * @param sink
  * @param scheduler
  * @param obs
  * @tparam In
  * @tparam A
  */
final class Pipeline[In, A] private (val matchId: UUID,
                                     val root: DataSource,
                                     val logicalSource: DataSource,
                                     val steps: Seq[Pipeline.ChainStep],
                                     val sink: DataSink.Aux[In, A],
                                     scheduler: Scheduler,
                                     val obs: Observable[In]) {

  override def toString: String = {
    s"Pipeline($matchId: ${steps.mkString(s"[ $root --> ", " -> ", s" ] $sink")})"
  }
  val result = {
    val src = logicalSource
    sink.connect(src.contentType, obs, src.metadata)(scheduler)
  }

  // TODO - fix this cast
  def resultFuture: CancelableFuture[A] = result.asInstanceOf[CancelableFuture[sink.Output]]

  def cancel(): Unit = result.cancel()
}

object Pipeline {

  def from[In, Out](id: UUID, source: DataSource, transforms: Seq[(String, Transform)], sink: DataSink.Aux[In, Out])(
      implicit scheduler: Scheduler): Either[String, Pipeline[In, Out]] = {
    apply(id, source, transforms, sink)(identity)
  }

  /**
    * The place where a source has its transformations applied after having first validated the ContentTypes line-up
    *
    * @param source
    * @param steps
    * @return
    */
  private[pipelines] def createPipeline(source: DataSource, steps: Seq[Pipeline.ChainStep]): DataSource = {
    val Some(logicalSource) = steps.foldLeft(Option(source)) {
      case (Some(src), Step(t, _, _)) =>
        t.applyTo(src)
      case other => sys.error(s"this will never happen. I promise: $other")
    }
    logicalSource
  }

  /**
    *
    * @param id
    * @param source
    * @param transforms
    * @param sink
    * @param prepare
    * @param scheduler
    * @tparam In
    * @tparam Out
    * @return
    */
  def apply[In, Out](id: UUID, source: DataSource, transforms: Seq[(String, Transform)], sink: DataSink.Aux[In, Out])(prepare: Observable[In] => Observable[In])(
      implicit scheduler: Scheduler): Either[String, Pipeline[In, sink.Output]] = {

    val steps: Either[String, Seq[ChainStep]] = ChainStep.connect(source, transforms)
    ChainStep.reduce(steps, id, sink)(prepare)
  }

  sealed trait ChainStep {
    def output: ContentType
  }

  object ChainStep {
    def reduce[In, Out](stepsEither: Either[String, Seq[ChainStep]], id: UUID, sink: DataSink.Aux[In, Out])(prepare: Observable[In] => Observable[In])(
        implicit scheduler: Scheduler): Either[String, Pipeline[In, Out]] = {
      stepsEither match {
        case Right(Root(source) +: steps) =>
          val ok = steps.lastOption.fold(true)(_.output.matches(sink.inputType))

          if (ok) {
            try {
              val logicalSource       = createPipeline(source, steps)
              val x                   = logicalSource.asObservable[In]
              val obs: Observable[In] = prepare(x)
              Right(new Pipeline[In, sink.Output](id, source, logicalSource, steps, sink, scheduler, obs))
            } catch {
              case NonFatal(e) =>
                Left(s"Error connecting $source with $sink: $e")
            }
          } else {
            Left(s"Can't connect ${source.contentType} with ${sink.inputType}")
          }

        case Left(err) => Left(err)
      }
    }

    /** @param source the initial data source
      * @param transforms the transformations to apply
      * @return either an error or a 'logical' source which encapsulates all the transforms
      */
    def connect(source: DataSource, transforms: Seq[(String, Transform)]): Either[String, Seq[ChainStep]] = {
      val (chainSeq, errorOpt) = transforms.foldLeft(Seq[ChainStep](Root(source)) -> Option.empty[String]) {
        case ((chain @ head +: _, None), (name, t)) =>
          t.outputFor(head.output) match {
            case Some(next) => (Step(t, name, next) +: chain) -> None
            case None =>
              val err = s"'$t' can't be applied to '${head.output}'"
              chain -> Option(err)
          }
        case (none, _) => none
      }
      errorOpt match {
        case None => Right(chainSeq.reverse)
        case Some(err) =>
          val types = chainSeq.reverse.map(_.output)
          val msg =
            s"Can't connect source with type ${source.contentType} through ${transforms.size} transforms as the types don't match: ${types.mkString("->")}: $err"
          Left(msg)
      }
    }
  }
  case class Root(val source: DataSource) extends ChainStep {
    override def output = source.contentType
  }
  case class Step(val transform: Transform, name: String, override val output: ContentType) extends ChainStep
}
