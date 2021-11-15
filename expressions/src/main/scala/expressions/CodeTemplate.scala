package expressions

import expressions.template.Context

import javax.script.{ScriptEngine, ScriptEngineFactory}
import scala.annotation.nowarn
import scala.reflect.ClassTag
import scala.util.control.NonFatal
import scala.util.{Failure, Try}

/**
  * Provides a script-able means to produce some type B for any type A
  */
object CodeTemplate {

  type Expression[A, B] = Context[A] => B

  case class Compiled[A, B](code: String, inputType: String, thunk: A => B) extends (A => B) {
    override def apply(input: A): B = thunk(input)

    override def toString(): String = s"COMPILED [$inputType] => B:\n${code}\n"
  }

  case class CompiledExpression[A, B](contextType: String, code: String, thunk: Expression[A, B]) extends Expression[A, B] {
    override def apply(input: Context[A]): B = thunk(input)

    override def toString(): String = s"COMPILED Context[$contextType] => B:\n${code}\n"
  }

  def const[A, B](value: B): Expression[A, B] = _ => value

  def newCache[A: ClassTag, B](scriptPrefix: String = ""): Cache[Expression[A, B]] = new Cache[Expression[A, B]](script => apply[A, B](script, scriptPrefix))

  /**
    * We bias these expressions for [[DynamicJson]] inputs
    *
    * @param expression
    * @tparam B
    * @return
    */
  def apply[A: ClassTag, B](expression: String, scriptPrefix: String = ""): Try[Expression[A, B]] = {
    val scriptWithImplicitJson =
      s"""
         |
         |$scriptPrefix
         |$expression
         |
         |""".stripMargin
    forAnyInput[A, B]("Message[DynamicJson, DynamicJson]", scriptWithImplicitJson)
  }

  /**
    * The resulting code is intended to work for a [[Context]] that has a [[DynamicJson]] as a message content.
    *
    * This allows scripts to work more fluidly w/ json messages in a scripting style, such as:
    * {{{
    *
    * }}}
    *
    * @param expression
    * @tparam A
    * @tparam B
    * @return
    */
  def forAnyInput[A: ClassTag, B](expression: String): Try[Expression[A, B]] = forAnyInput(className[A], expression)

  def forAnyInput[A, B](contextType: String, expression: String): Try[Expression[A, B]] = {
    val script =
      s"""import expressions._
         |import expressions.implicits._
         |//import AvroExpressions._
         |import expressions.template.{Context, Message}
         |
         |(context : Context[${contextType}]) => {
         |  import context._
         |  $expression
         |}
       """.stripMargin
    compileAsExpression[A, B](contextType, script)
  }

  def compileAsExpression[A, B](contextType: String, script: String): Try[Expression[A, B]] = {
    compile[Context[A], B](contextType, script).map {
      case Compiled(expr, script, thunk: Expression[A, B]) => CompiledExpression(expr, script, thunk)
    }
  }

  def compile[A: ClassTag, B](script: String): Try[Compiled[A, B]] = compile(className[A], script)

  def scalaEngine() = {
    val manager = new javax.script.ScriptEngineManager(getClass().getClassLoader())
    manager.getEngineByName("scala")
  }
  def compile[A: ClassTag, B](inputType: String, script: String): Try[Compiled[A, B]] = {
    type Thunk = A => B

    val thunk = try {
      val engine: ScriptEngine = scalaEngine()
      val result               = engine.eval(script)

      result match {
        case expr: Thunk @nowarn => Try(Compiled(script, inputType, expr))
        case other =>
          Failure(new Exception(s"Couldn't parse '$script' as an Expression[$className]: $other"))
      }
    } catch {
      case NonFatal(err) => Failure(new Exception(s"Couldn't parse '$script' as an Expression[$className] : $err", err))
    }
    thunk
  }

  private[expressions] def className[A: ClassTag] = implicitly[ClassTag[A]].runtimeClass match {
    case other if other.isPrimitive => other.getName.capitalize
    case other                      => other.getName
  }
}
