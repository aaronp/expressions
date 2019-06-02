package pipelines.reactive

import monix.reactive.Observable

import scala.reflect.runtime.universe._

class ObservableSyntax[A](val obs: Observable[A]) extends AnyVal {
  def asDataSource(metadata: Map[String, String] = Map.empty)(implicit typeTag: TypeTag[A]): DataSource = {
    DataSource.of(ContentType.of[A], obs, metadata)
  }
  def asDataSource(metadata: (String, String), theRest: (String, String)*)(implicit typeTag: TypeTag[A]): DataSource = {
    val map = theRest.toMap + metadata
    asDataSource(map.ensuring(_.size == theRest.size + 1, "duplicate metadata keys"))
  }

}
