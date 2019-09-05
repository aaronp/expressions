package expressions

import io.circe._

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.language.existentials

/** Exposes a means of converting text syntax based on circe optics into a 'Json => Boolean' predicate
  */
object JsonExpressions {

  type Predicate = Json => Boolean

  def newCache: Cache[Predicate] = new Cache[Predicate]((asPredicate _), _ => true)

  /**
    * The inputExpression is expected to refer to the json value as 'it' (a bit like Kotlin, but less shit).
    *
    * So :
    * {{{
    *   it.foo.bar <= 5
    * }}}
    *
    * the monocle expression of 'it.foo.bar' gets pimped into a [[RichJsonPath]].
    *
    * @param inputExpr
    * @return
    */
  def asPredicate(inputExpr: String): Try[Predicate] = {
    val expr = inputExpr.replace(" == ", " ===== ")
    val script =
      s"""import expressions.implicits._
         |import io.circe.optics.JsonPath._
         |import io.circe._
         |
         |(inputJson : Json) => {
         |  implicit val implicitInputJson = inputJson
         |  val it = root
         |  $expr
         |}
       """.stripMargin

    try {
      val tree   = compiler.parse(script)
      val result = compiler.eval(tree)
      result match {
        case p: Predicate => Success(p)
        case other        => Failure(new Exception(s"Couldn't parse '$script' as a json predicate : $other"))
      }
    } catch {
      case NonFatal(err) =>
        Failure(new Exception(s"Couldn't parse '$script' as a json predicate : $err", err))
    }
  }

  private def compiler = AvroExpressions.compiler

}
