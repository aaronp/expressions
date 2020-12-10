package expressions

import expressions.JsonTemplate._

import scala.reflect.ClassTag
import scala.util.Try

/**
  * Functions for scripting string interpolation.
  *
  * e.g turn some [[Context]] into a string, with use-cases like:
  *
  * {{{
  *   someJsonDoc = """ { "key" : "{{ record.key.toUpperCase }}", "foo" : "{{ if (record.value.path.to.foo) "x" else "y" }}", "double-host" : "{{ env.HOST * 2 }}"  } """
  * }}}
  *
  */
object StringTemplate {

  type StringExpression[A] = JsonTemplate.Expression[A, String]

  def newCache[A: ClassTag]: Cache[StringExpression[A]] = new Cache[StringExpression[A]](script => Try(apply[A](script)))

  /**
    * Consider the initial remainingExpressionStr:
    * {{{
    *   "foo {{ x }} bar {{ y * 2 }} bazz"
    * }}}
    * This should get translated into a function whose body looks like:
    *
    * {{{
    *  val string1 = "foo "
    *  val string2 = { x.toString() }
    *  val string3 = " bar "
    *  val string4 = { (y * 2).toString }
    *  val string5 = " bazz"
    *  string1 + string2 + string3 + string4 + string5
    * }}}
    *
    * @param expression
    * @tparam A
    * @return a mapping of variable names to their RHS expressions (constants or functions)
    */
  def apply[A: ClassTag](expression: String): StringExpression[A] = {
    val parts = resolveExpressionVariables(expression, Nil)
    parts.size match {
      case 0 => const[A]("")
      case 1 => const[A](expression)
      case _ =>
        val script = stringAsExpression(parts)
        JsonTemplate.compileAsExpression[A, String](script).get
    }
  }

  def const[A](value: String): StringExpression[A] = _ => value

  private def resolveExpressionVariables(remainingExpressionStr: String, expressions: List[String]): List[String] = {
    remainingExpressionStr match {
      case Moustache(before, expression, after) =>
        val updated = s"{ $expression }.toString()" +: quote(before) +: expressions
        resolveExpressionVariables(after, updated)
      case "" => expressions.reverse
      case literal =>
        (quote(literal) +: expressions).reverse
    }
  }

  private def stringAsExpression[A: ClassTag](parts: Seq[String]) = {
    val scriptHeader =
      s"""import expressions._
         |import AvroExpressions._
         |import expressions.template.{Context, Message}
         |
         |(context : Context[${className[A]}]) => {
         |  import context._
         |
       """.stripMargin

    def asVar(i: Int) = s"_stringPart$i"
    val concat = parts.indices.map { i =>
      "${" + asVar(i) + "}"
    }
    val scriptFooter =
      s"""
           |    ${concat.mkString("s\"", "", "\"")}
           |}
           |""".stripMargin
    val resolved = parts.zipWithIndex.map {
      case (rhs, i) =>
        s"val ${asVar(i)} = $rhs"
    }
    resolved.mkString(scriptHeader, "\n", scriptFooter)
  }
}
