package expressions.franz

object ToDart {

  lazy val ClassName = ".*class (.*?)\\(.*".r
  lazy val ParamPattern = ".*?(\\w+) *: *([A-Za-z0-9\\[\\]]+).*"
  lazy val ParamR = ParamPattern.r
  lazy val ParamWithDefaultR = (ParamPattern + " *= *(.*)").r


  def main(a: Array[String]) = {

    Seq(
      """case class MappingEntry(topic : String, filePath  :String)
        |""".stripMargin

    ).map(asDart).foreach(println)
  }

  case class Form(questions : List[Question])
  case class Question(text : String, values : List[Choice], kind : String, required : Boolean, weight : Double)
  case class Choice(text : String, score : Int)
  //Valueslider, single, multiple, text

  implicit class Tap[A](val value: A) extends AnyVal {
    def tap(f: A => Unit): A = {
      f(value)
      value
    }
  }


  lazy val OptR = "Option\\[(.*)\\]".r
  lazy val SeqR = "Seq\\[(.*)\\]".r
  lazy val LstR = "List\\[(.*)\\]".r
  lazy val SetR = "Set\\[(.*)\\]".r
  lazy val MapR = "Map\\[(.*),(.*)\\]".r

  def typAsDart(t: String): String = t match {
    case OptR(t) => typAsDart(t)
    case SeqR(t) => s"List<${typAsDart(t)}>"
    case LstR(t) => s"List<${typAsDart(t)}>"
    case SetR(t) => s"Set<${typAsDart(t)}>"
    case MapR(k, v) => s"Map<${typAsDart(k)}, ${typAsDart(v)}>"
    case "Int" | "Long" | "Short" => "int"
    case "Double" => "double"
    case "Boolean" => "bool"
    case other => other
  }

  case class Parameter(name: String, scalaType: String, default: Option[String]) {
    require(scalaType != null, "scalaType is null")

    def dartType: String = typAsDart(scalaType)

    def dartInitializer: String = scalaType match {
      case OptR(_) => " = null"
      case SeqR(_) => " = []"
      case SetR(_) => " = {}"
      case LstR(_) => " = []"
      case MapR(_,_) => " = {}"
      case _ => ""
    }
  }

  object Params {
    def unapply(line: String): Option[Seq[Parameter]] = {
      val found = line.split(",", -1).toList.collect {
        case ParamWithDefaultR(name, typ, d) => Parameter(name, typ, Some(d.trim))
        case ParamR(name, typ) => Parameter(name, typ, None)
      }
      if (found.isEmpty) None else Some(found)
    }
  }

  case class Definition(className: String, params: Seq[Parameter]) {
    def asDartCode: String = {
      s"""class $className {
         |  ${params.map(p => s"this.${p.name}").mkString(s"${className}(\n\t", ",\n\t", "\n\t);")}
         |${params.map(p => s"${p.dartType} ${p.name}${p.dartInitializer};").mkString("\n    ", "\n    ", "")}
         |
         |  Map<String, Object> get asJson {
         |    return {
         |${params.map(p => s"        '${p.name}': ${p.name}").mkString(",\n")}
         |    };
         |  }
         |
         |  static ${className} fromJson(Map<String, dynamic> json) {
         |    return ${className}(
         |${params.map(p => s"        json['${p.name}']").mkString("", ",\n", ");")}
         |  }
         |}""".stripMargin
    }
  }

  object Definition {
    def parse(scalaClass: String): Definition = {
      val lines = scalaClass.linesIterator.toList
      val name = lines.collectFirst {
        case ClassName(n) => n
      }
      val params = lines.collect {
        case Params(all) => all
      }.flatten
      Definition(name.get, params)
    }
  }

  def asDart(scalaClass: String) = Definition.parse(scalaClass).asDartCode

}