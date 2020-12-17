package expressions

object Unquote {
  private val UnquoteR        = """ *"(.*)" *""".r
  private val UnquoteEscapedR = """ *\\"(.*)\\" *""".r

  def apply(value: String): String = value match {
    case UnquoteR(x)        => x
    case UnquoteEscapedR(x) => x
    case x                  => x
  }
}
