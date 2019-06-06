package pipelines.reactive

import monix.execution.{Ack, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}

import scala.concurrent.Future

/**
  * Represents a data source -- some type coupled with a means of consuming that data
  */
trait DataSource extends HasMetadata {

  /** the type DataSource
    */
  type T <: DataSource

  final def addMetadata(key: String, value: String): T = addMetadata(Map(key -> value))

  def addMetadata(entries: Map[String, String]): T

  /** @return the content type of this data source
    */
  def contentType: ContentType
  def data(ct: ContentType): Option[Observable[_]]
  def asObservable: Observable[_] = {
    data(contentType).getOrElse {
      sys.error(s"${this} wasn't able to provide an observable for its own content type '$contentType'")
    }
  }
}

object DataSource {

  object syntax extends LowPriorityDataSourceImplicits
  import scala.reflect.runtime.universe._

  def of(contentType: ContentType, obs: Observable[_], metadata: Map[String, String] = Map.empty): DataSource = {
    new AnonTypeDataSource(contentType, obs, metadata)
  }

  def apply[T: TypeTag](observable: Observable[T]): DataSource = {
    apply(ContentType.of[T], observable)
  }

  def apply[T](contentType: ContentType, observable: Observable[T], metadata: Map[String, String] = Map.empty): DataSource = {
    new SingleTypeDataSource(contentType, observable, metadata)
  }

  def push[A: TypeTag](metadata: Map[String, String])(implicit sched: Scheduler): PushSource[A] = {
    val (input: Observer[A], output: Observable[A]) = Pipe.publish[A].multicast
    new PushSource[A](ContentType.of[A], input, output, metadata)
  }

  def createPush[A: TypeTag]: Scheduler => DataSource = createPush[A](ContentType.of[A])

  def createPush[A](contentType: ContentType): Scheduler => DataSource = {
    val ns = { implicit sched :Scheduler=>
      push[A](contentType)
    }
    ns
  }

  def push[A](contentType: ContentType, metadata: Map[String, String] = Map.empty)(implicit sched: Scheduler): PushSource[A] = {
    val (input: Observer[A], output: Observable[A]) = Pipe.publish[A].multicast
    new PushSource[A](contentType, input, output, metadata)
  }

  class PushSource[A](override val contentType: ContentType, val input: Observer[A], obs: Observable[A], override val metadata: Map[String, String]) extends DataSource {
    override type T = PushSource[A]
    def addMetadata(entries: Map[String, String]): T = {
      new PushSource(contentType, input, obs, metadata ++ entries)
    }

    def push(value: A): Future[Ack] = input.onNext(value)
    override def data(ct: ContentType): Option[Observable[_]] = {
      if (ct == contentType) {
        Option(obs)
      } else {
        None
      }
    }
  }

  private case class AnonTypeDataSource(override val contentType: ContentType, observable: Observable[_], override val metadata: Map[String, String]) extends DataSource {

    override type T = AnonTypeDataSource
    def addMetadata(entries: Map[String, String]): T = {
      copy(metadata = metadata ++ entries)
    }

    override def data(ct: ContentType): Option[Observable[_]] = {
      if (ct == contentType) {
        Option(observable)
      } else {
        None
      }
    }
  }

  private case class SingleTypeDataSource(override val contentType: ContentType, observable: Observable[_], override val metadata: Map[String, String]) extends DataSource {

    override type T = SingleTypeDataSource
    def addMetadata(entries: Map[String, String]): T = {
      copy(metadata = metadata ++ entries)
    }
    override def data(ct: ContentType): Option[Observable[Any]] = {
      if (contentType == ct) {
        Option(observable)
      } else {
        contentType match {
          case ClassType("Tuple2", Seq(t1, t2)) =>
            observable match {
              case x: Observable[(_, _)] if t1 == ct => Option(x.map(_._1))
              case x: Observable[(_, _)] if t2 == ct => Option(x.map(_._2))
              case _                                 => None
            }
          case _ => None
        }
      }
    }
  }
}
