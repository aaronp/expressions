package expressions

import io.circe.Json
import io.circe.optics.{JsonPath, JsonTraversalPath}
import monocle.{Optional, Traversal}

trait LowPriorityOptionalImplicits {
  implicit def asRichOptional[A](value: Optional[Json, A])           = new RichOptional[A](value)
  implicit def asRichJPath(path: JsonPath)                           = new RichJsonPath(path)
  implicit def asRichTraversal[A](traversal: Traversal[Json, A])     = new RichTraversalPath[A](traversal)
  implicit def asRichJPathTraversal[A](traversal: JsonTraversalPath) = asRichTraversal(traversal.json)
}
