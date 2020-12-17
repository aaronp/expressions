package expressions.template

import eie.io._

import java.nio.file.Path
import scala.language.dynamics

class FileSystem(val dir: Path) extends AnyVal with Dynamic {
  def selectDynamic(fieldName: String): String = {
    dir.resolve(fieldName).text
  }
}
case class Env(env: Map[String, String] = sys.env) extends Dynamic {
  def selectDynamic(fieldName: String): String = env.get(fieldName).getOrElse("")
}
case class Message[A](value: A, key: String = "", timestamp: Long = 0, headers: Map[String, String] = Map.empty, topic: String = "") {
  def withKey(k: String)                            = copy(key = k)
  def asContext(dir: Path = ".".asPath): Context[A] = Context(this, dir)
}

case class Context[A](record: Message[A], env: Env, fs: FileSystem) {
  def withEnv(first: (String, String), theRest: (String, String)*): Context[A] = withEnv((first +: theRest).toMap)
  def withEnv(newEnv: Map[String, String]): Context[A]                         = copy(env = Env(env.env ++ newEnv))
  def replaceEnv(newEnv: Map[String, String]): Context[A]                      = copy(env = Env(newEnv))
}
object Context {
  def apply[A](record: Message[A], dir: Path = ".".asPath): Context[A] = Context(record, Env(), new FileSystem(dir))
}
