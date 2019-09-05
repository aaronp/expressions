package expressions

import io.circe.Json
import monocle.Traversal

final class RichTraversalPath[A](val traversal: Traversal[Json, A]) extends AnyVal {
  def toList(implicit json: Json): List[A] = traversal.getAll(json)
}
