package expressions

import javax.script.{Invocable, ScriptContext, ScriptEngine, ScriptEngineManager, SimpleScriptContext}

import util.Try


@main def run() = {
  Aaron.wtf2("args!")
}

object Aaron {

  def mkEngine: ScriptEngine = {
    val m = new javax.script.ScriptEngineManager(getClass().getClassLoader())

    import scala.jdk.CollectionConverters._
    val list = m.getEngineFactories.asScala
    val all = list.map { e =>
      e.getNames.asScala.mkString(s"(${e.getEngineName} ${e.getLanguageName} version ${e.getLanguageVersion}) Names: \n", "\n\t", "\n")
    }
//    println(all.mkString("\n\n"))
    m.getEngineByName("scala")
  }

  def wtf = {
    import javax.script.Invocable
    import javax.script.ScriptEngine
    import javax.script.ScriptEngineManager
    val engine = new ScriptEngineManager().getEngineByName("nashorn")
    engine.eval("""var fun1 = function(name) {
                  |    print('Hi there from Javascript, ' + name);
                  |    return "greetings from javascript";
                  |};
                  |
                  |var fun2 = function (object) {
                  |    print("JS Class Definition: " + Object.prototype.toString.call(object));
                  |};""".stripMargin)

    val invocable = engine.asInstanceOf[Invocable]
    invocable.invokeFunction("fun1", "Peter Parker")
  }

  def wtf2(name : String) = {
    import javax.script.Invocable
    import javax.script.ScriptEngine
    import javax.script.ScriptEngineManager
    val engine = new ScriptEngineManager().getEngineByName("scala")
    engine.eval("""
                  |val fun1 = (name : String) => {
                  |    println("Hi there from Scala" + name)
                  |    "Hi there from Scala" + name
                  |}
                  |""".stripMargin)

    val invocable = engine.asInstanceOf[dotty.tools.repl.ScriptEngine]
    val function = invocable.eval("""fun1""")
    println(function)
    val f= function.asInstanceOf[String => Unit]
    f(name)
  }
  def test2(x: String) = {
    import javax.script.{Bindings, ScriptContext, ScriptEngine, ScriptEngineManager, SimpleScriptContext}
    val manager = new ScriptEngineManager
    val engine = manager.getEngineByName("scala")
    engine.put("x", "hello")
    // print global variable "x"
    engine.eval("println(x)")
    // the above line prints "hello"
    // Now, pass a different script context
    val newContext = new SimpleScriptContext
    val engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE)
    // add new variable "x" to the new engineScope
    engineScope.put("x", "world")
    // execute the same script - but this time pass a different script context
    engine.eval("println(x)", newContext)
    // the above line prints "world"
  }

  def test = {

    val engine = mkEngine

    val script =
      """def testme(input: String) = {
        |  println("hello scala! input is " + input)
        |}""".stripMargin

    engine.eval(script)

    engine match {
      case x: Invocable =>
        Try(x.invokeMethod("testme", "wo0t!"))
        Try(x.invokeFunction("testme", "wo0t!"))

    }
  }

  def test3(x: String) = {

    val engine = mkEngine

    val newContext = new SimpleScriptContext()
    val engineScope = newContext.getBindings(ScriptContext.ENGINE_SCOPE)
    //    val bindings = engine.getBindings(ScriptContext.ENGINE_SCOPE)
    engineScope.put("input", x)

    val script =
      """def testme(input: String) = {
        |  println("hello scala! input is " + input)
        |}""".stripMargin

    //    engine.put()
    val b = newContext.getBindings(ScriptContext.ENGINE_SCOPE)
    println(s"Check: " + b.get("input"))
    val result = engine.eval(script, newContext)
    println(result)
  }

}