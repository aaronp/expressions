package pipelines.reactive

import monix.reactive.Observable

trait LowPriorityDataSourceImplicits {
  implicit def asRichObservable[A](obs : Observable[A]) = {
    new ObservableSyntax[A](obs)
  }
}
