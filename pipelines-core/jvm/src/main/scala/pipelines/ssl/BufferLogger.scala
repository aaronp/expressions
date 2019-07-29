package pipelines.ssl

import scala.collection.mutable.ArrayBuffer
import scala.sys.process.ProcessLogger

final class BufferLogger extends ProcessLogger {
  private val outputBuffer = ArrayBuffer[String]()
  private val errorBuffer  = ArrayBuffer[String]()
  private val bothBuffer   = ArrayBuffer[String]()

  def allOutput   = bothBuffer.mkString("\n")
  def stdOutput   = outputBuffer.mkString("\n")
  def errorOutput = errorBuffer.mkString("\n")

  override def out(s: => String): Unit = {
    outputBuffer.append(s)
    bothBuffer.append(s)
  }

  override def err(s: => String): Unit = {
    errorBuffer.append(s)
    bothBuffer.append(s)
  }

  override def buffer[T](f: => T): T = f
}
