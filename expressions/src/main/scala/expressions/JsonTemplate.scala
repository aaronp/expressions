package expressions

import expressions.AvroExpressions.compiler
import expressions.StringTemplate.StringExpression
import expressions.template.Context

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Provides a script-able means to produce some type A for any type A
  */
object JsonTemplate {

  type Expression[A, B] = Context[A] => B

  def const[A, B](value: B): Expression[A, B] = _ => value

  /**
    * We bias these expressions for [[RichDynamicJson]] inputs
    * @param expression
    * @tparam B
    * @return
    */
  def apply[B](expression: String): Try[Expression[RichDynamicJson, B]] = {
    val scriptWithImplicitJson =
      s"""
         |implicit val implicitMessageValueSoRichJsonPathAndOthersWillWork = context.record.value.jsonValue
         |
         |$expression
         |
         |""".stripMargin
    forAnyInput[RichDynamicJson, B](scriptWithImplicitJson)
  }

  /**
    * The resulting code is intended to work for a [[Context]] that has a [[RichDynamicJson]] as a message value.
    *
    * This allows scripts to work more fluidly w/ json messages in a scripting style, such as:
    * {{{
    *
    * }}}
    * @param expression
    * @tparam A
    * @tparam B
    * @return
    */
  def forAnyInput[A: ClassTag, B](expression: String): Try[Expression[A, B]] = {
    val script =
      s"""import expressions._
         |import expressions.implicits._
         |import AvroExpressions._
         |import expressions.template.{Context, Message}
         |
         |(context : Context[${className[A]}]) => {
         |  import context._
         |  $expression
         |}
       """.stripMargin
    compileAsExpression[A, B](script)
  }

  private[expressions] val Moustache = """(.*?)\{\{(.*?)}}(.*)""".r

  private[expressions] def compileAsExpression[A: ClassTag, B](script: String): Try[Expression[A, B]] = {
    try {
      val tree   = compiler.parse(script)
      val result = compiler.eval(tree)
      result match {
        case expr: Expression[A, B] => Success(expr)
        case other                  => Failure(new Exception(s"'$script' isn't an Expression[$className] : $other"))
      }
    } catch {
      case NonFatal(err) => Failure(new Exception(s"Couldn't parse '$script' as an Expression[$className] : $err", err))
    }
  }

  def quote(str: String) = {
    val q = "\""
    q + str + q
  }
  def className[A: ClassTag] = implicitly[ClassTag[A]].runtimeClass match {
    case other if other.isPrimitive => other.getName.capitalize
    case other                      => other.getName
  }
}
