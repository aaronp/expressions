package expressions.franz

import args4c.implicits._
import com.typesafe.config.Config

object ConfigAsJavaMap {

  def apply(config: Config): java.util.Map[String, String] = {
    val jMap = new java.util.HashMap[String, String]()
    config.entries().foreach {
      case (key, value) =>
        jMap.put(key, valueOf(value.render()))
    }
    jMap
  }

  private val UnquoteR = """ *"(.*)" *""".r

  private def valueOf(value: String) = value match {
    case UnquoteR(x) => x
    case x           => x
  }
}
