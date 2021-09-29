package expressions

import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success, Try}

/**
  * A really dumb, lazy cache of expressions
  */
class Cache[V](create: String => Try[V], default: Try[V] = Failure[V](new IllegalArgumentException("no default provided for empty script"))) extends StrictLogging {
  private object Lock

  private var thunkByCode = Map[String, V]()

  def map[A](thunk: V => A): Cache[A] = new Cache[A](create.andThen(_.map(thunk)))

  private def createUnsafe(expression: String): Try[V] = {
    val result = create(expression)

//    logger.debug(s"""Compiling:
//        |${expression}
//        |
//        |Yields: ${result.isSuccess}
//        |""".stripMargin)
    result.map { value =>
      thunkByCode = thunkByCode.updated(expression, value)
      value
    }
  }

  def apply(expression: String): Try[V] = {
    if (Option(expression).map(_.trim).exists(_.nonEmpty)) {
      Lock.synchronized {
        thunkByCode.get(expression) match {
          case None         => createUnsafe(expression)
          case Some(cached) => Success(cached)
        }
      }
    } else {
      default
    }
  }
}
