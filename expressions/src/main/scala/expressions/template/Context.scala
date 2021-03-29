package expressions.template

import eie.io._
import io.circe.{Decoder, Encoder, Json}

import java.nio.file.Path
import scala.language.dynamics

class FileSystem(val dir: Path) extends AnyVal with Dynamic {
  def selectDynamic(fieldName: String): String = {
    dir.resolve(fieldName).text
  }
}
object FileSystem {
  def apply(dir: Path = ".".asPath) = new FileSystem(dir)
}
case class Env(env: Map[String, String] = sys.env) extends Dynamic {
  def selectDynamic(fieldName: String): String = env.getOrElse(fieldName, "")
}
case class Message[K, V](content: V, key: K, timestamp: Long = 0, headers: Map[String, String] = Map.empty, topic: String = "", offset: Long = 0, partition: Int = 0) {
  def withKey[A](k: A): Message[A, V] =
    Message[A, V](content, k, timestamp, headers, topic, offset, partition)
  def asContext(dir: Path = ".".asPath): Context[Message[K, V]] = Context(this, Env(), FileSystem(dir))
}
object Message {
  def of[A](value: A, key: String = "", timestamp: Long = 0, headers: Map[String, String] = Map.empty, topic: String = ""): Message[String, A] = {
    new Message[String, A](value, key, timestamp, headers, topic)
  }

  implicit def msgEncoder[K: Encoder, V: Encoder]: Encoder[Message[K, V]] = Encoder[Message[K, V]] { msg =>
    import io.circe.syntax._
    import msg._
    Json.obj(
      "content"   -> Encoder[V].apply(content),
      "key"       -> Encoder[K].apply(key),
      "timestamp" -> timestamp.asJson,
      "headers"   -> headers.asJson,
      "topic"     -> topic.asJson,
      "offset"    -> offset.asJson,
      "partition" -> partition.asJson
    )
  }
  implicit def msgDecoder[K: Decoder, V: Decoder]: Decoder[Message[K, V]] = Decoder[Message[K, V]] { cursor =>
    for {
      content   <- cursor.downField("content").as[V]
      key       <- cursor.downField("key").as[K]
      timestamp <- cursor.downField("timestamp").as[Long]
      headers   <- cursor.downField("headers").as[Map[String, String]]
      topic     <- cursor.downField("topic").as[String]
      offset    <- cursor.downField("offset").as[Long]
      partition <- cursor.downField("partition").as[Int]
    } yield Message(content, key, timestamp, headers, topic, offset, partition)
  }

}

/**
  * The context is passed to some black-box function which is intended to compute a result
  * @tparam A
  */
case class Context[A](record: A, env: Env, fs: FileSystem) {
  def withEnv(first: (String, String), theRest: (String, String)*): Context[A] = withEnv((first +: theRest).toMap)
  def withEnv(newEnv: Map[String, String]): Context[A]                         = copy(env = Env(env.env ++ newEnv))
  def replaceEnv(newEnv: Map[String, String]): Context[A]                      = copy(env = Env(newEnv))
}
object Context {
  def apply[A](record: A, fs: FileSystem = FileSystem()): Context[A] = Context(record, Env(), fs)
}
