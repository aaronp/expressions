package pipelines.reactive
import monix.execution.{Cancelable, CancelableFuture, Scheduler}
import monix.reactive.Observable

/**
  * A sink which simply registers the computed source as another source
  *
  * @param metadata
  * @param sources
  */
case class RegisterAsSourceSink(override val metadata: Map[String, String], sources: Sources) extends DataSink {
  override type T             = RegisterAsSourceSink
  override type Input         = Any
  override type Output        = Unit
  override type ConnectResult = CancelableFuture[Unit]

  override def inputType: ContentType = ContentType.any

  override def addMetadata(entries: Map[String, String]) = copy(metadata = metadata ++ entries)

  override def connect(contentType: ContentType, observable: Observable[Any], sourceMetadata: Map[String, String])(implicit scheduler: Scheduler): CancelableFuture[Unit] = {
    val prefix      = metadata.getOrElse("prefix", "registered")
    val newMetadata = HasMetadata(sourceMetadata).prefixedMetadata(prefix)
    val (_, ack)    = sources.add(DataSource.of(contentType, observable, newMetadata))
    CancelableFuture(ack.map(_ => Unit), Cancelable())
  }
}
