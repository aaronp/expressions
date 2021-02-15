package expressions

import expressions.JsonTemplate._
import expressions.template.Message

import scala.reflect.ClassTag
import scala.util.Try

/**
  * Functions for scripting string interpolation.
  *
  * e.g turn some [[Context]] into a string, with use-cases like:
  *
  * {{{
  *   someJsonDoc = """ { "key" : "{{ record.key.toUpperCase }}", "foo" : "{{ if (record.content.path.to.foo) "x" else "y" }}", "double-host" : "{{ env.HOST * 2 }}"  } """
  * }}}
  *
  */
object StringTemplate {

  type StringExpression[A] = JsonTemplate.Expression[A, String]

  def newCache[K: ClassTag, V: ClassTag](scriptPrefix: String = ""): Cache[StringExpression[Message[K, V]]] = {
    new Cache[StringExpression[Message[K, V]]](script => Try(apply[K, V](script, scriptPrefix)))
  }

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
  def apply[K: ClassTag, V: ClassTag](expression: String, scriptPrefix: String = ""): StringExpression[Message[K, V]] = {
    val parts = resolveExpressionVariables(expression, Nil)
    parts.size match {
      case 0 => const[Message[K, V]]("")
      case 1 => const[Message[K, V]](expression)
      case _ =>
        val keyType     = className[K]
        val valueType   = className[V]
        val contextType = s"Message[$keyType, $valueType]"
        val script      = stringAsExpression(contextType, scriptPrefix, parts)
        JsonTemplate.compileAsExpression[Message[K, V], String](contextType, script).get
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

  private def stringAsExpression[A: ClassTag](scriptPrefix: String, parts: Seq[String]): String = {
    stringAsExpression[A](className[A], scriptPrefix, parts)
  }

  private def stringAsExpression[A](contextType: String, scriptPrefix: String, parts: Seq[String]): String = {
    val scriptHeader =
      s"""import expressions._
         |import expressions.implicits._
         |import AvroExpressions._
         |import expressions.template.{Context, Message}
         |
         |(context : Context[${contextType}]) => {
         |  import context._
         |  ${scriptPrefix}
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
