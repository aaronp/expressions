package expressions

import expressions.AvroExpressions.compiler
import expressions.template.Context

import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * Provides a script-able means to produce some type A for any type A
  */
object JsonTemplate {

  type Expression[A, B] = Context[A] => B

  case class CompiledExpression[A: ClassTag, B](code: String, thunk: Expression[A, B]) extends Expression[A, B] {
    override def apply(input: Context[A]): B = thunk(input)

    def aType                       = implicitly[ClassTag[A]].runtimeClass.getSimpleName
    override def toString(): String = s"COMPILED Context[$aType] => B:\n${code}\n"
  }

  def const[A, B](value: B): Expression[A, B] = _ => value

  def newCache[B](scriptPrefix: String = ""): Cache[Expression[RichDynamicJson, B]] = new Cache[Expression[RichDynamicJson, B]](script => apply[B](script, scriptPrefix))

  /**
    * We bias these expressions for [[RichDynamicJson]] inputs
    * @param expression
    * @tparam B
    * @return
    */
  def apply[B](expression: String, scriptPrefix: String = ""): Try[Expression[RichDynamicJson, B]] = {
    val scriptWithImplicitJson =
      s"""
         |implicit val implicitMessageValueSoRichJsonPathAndOthersWillWork = context.record.value.jsonValue
         |
         |$scriptPrefix
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
        case expr: Expression[A, B] => Success(CompiledExpression(script, expr))
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
