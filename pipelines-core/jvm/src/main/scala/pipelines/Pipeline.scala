package pipelines

import pipelines.reactive.{DataSink, DataSource, Transform}

case class Pipeline private (source: DataSource, transforms: Seq[Transform], sink: DataSink) {}

object Pipeline {
  def apply(source: DataSource, transforms: Seq[Transform], sink: DataSink) = {}

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
